package com.uniace.trackr.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.uniace.trackr.config.TrackrProperties;
import com.uniace.trackr.domain.DeviceCredential;
import com.uniace.trackr.domain.Employee;
import com.uniace.trackr.domain.UserAccount;
import com.uniace.trackr.domain.WebAuthnChallenge;
import com.uniace.trackr.domain.enums.ChallengePurpose;
import com.uniace.trackr.domain.enums.CredentialStatus;
import com.uniace.trackr.dto.WebAuthnDtos.*;
import com.uniace.trackr.repo.DeviceCredentialRepository;
import com.uniace.trackr.repo.WebAuthnChallengeRepository;
import com.uniace.trackr.util.TokenUtil;
import com.uniace.trackr.web.error.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Device fingerprint authentication built on WebAuthn platform authenticators.
 *
 * <p>What this does and does not do, stated plainly:
 *
 * <ul>
 *   <li>The employee's fingerprint never leaves their phone. The phone uses it
 *       to unlock a private key; Trackr only ever sees a signature and stores
 *       only the matching public key.</li>
 *   <li>Verification requires the server: a signature is checked against a
 *       challenge this server issued. An event captured offline therefore
 *       carries the device's assertion and is verified when it syncs - the
 *       phone cannot verify on the server's behalf, and the UI says so.</li>
 *   <li>Platform support varies. Where WebAuthn is unavailable the employee
 *       still records events; those events are stored with
 *       {@code biometricVerified = false} and are visible as such to
 *       supervisors. They are never presented as fingerprint-verified.</li>
 * </ul>
 */
@Service
public class WebAuthnService {

    private static final Logger log = LoggerFactory.getLogger(WebAuthnService.class);
    private static final ObjectMapper CBOR = new ObjectMapper(new CBORFactory());
    private static final ObjectMapper JSON = new ObjectMapper();

    private final TrackrProperties properties;
    private final WebAuthnChallengeRepository challengeRepository;
    private final DeviceCredentialRepository credentialRepository;

    public WebAuthnService(TrackrProperties properties,
                           WebAuthnChallengeRepository challengeRepository,
                           DeviceCredentialRepository credentialRepository) {
        this.properties = properties;
        this.challengeRepository = challengeRepository;
        this.credentialRepository = credentialRepository;
    }

    // -------------------------------------------------------------------------
    // Registration
    // -------------------------------------------------------------------------

    @Transactional
    public RegistrationOptions startRegistration(UserAccount user, Employee employee) {
        String challenge = issueChallenge(user, ChallengePurpose.REGISTRATION);

        List<Map<String, Object>> exclude = new ArrayList<>();
        for (DeviceCredential existing : credentialRepository.findByEmployeeIdOrderByCreatedAtDesc(employee.getId())) {
            if (existing.isActive()) {
                exclude.add(Map.of("type", "public-key", "id", existing.getCredentialId()));
            }
        }

        return new RegistrationOptions(
                Map.of("id", properties.getWebauthn().getRpId(),
                       "name", properties.getWebauthn().getRpName()),
                Map.of("id", TokenUtil.base64Url(employee.getId().toString().getBytes(StandardCharsets.UTF_8)),
                       "name", employee.getEmail(),
                       "displayName", employee.fullName()),
                challenge,
                List.of(Map.of("type", "public-key", "alg", CoseKeyParser.ALG_ES256),
                        Map.of("type", "public-key", "alg", CoseKeyParser.ALG_RS256)),
                120_000L,
                "none",
                Map.of("authenticatorAttachment", "platform",
                       "residentKey", "preferred",
                       "userVerification", "required"),
                exclude);
    }

    @Transactional
    public DeviceCredential finishRegistration(UserAccount user, Employee employee, RegistrationRequest request) {
        if (request.response() == null) {
            throw ApiException.badRequest("FINGERPRINT_SETUP_FAILED",
                    "We could not finish setting up your fingerprint. Please try again.");
        }

        ClientData clientData = readClientData(request.response().clientDataJSON());
        requireType(clientData, "webauthn.create");
        consumeChallenge(user, clientData.challenge(), ChallengePurpose.REGISTRATION);
        requireAllowedOrigin(clientData.origin());

        byte[] attestationObject = decode(request.response().attestationObject(), "attestationObject");
        byte[] authDataBytes;
        try {
            JsonNode attestation = CBOR.readTree(attestationObject);
            JsonNode authData = attestation.get("authData");
            if (authData == null) {
                throw new IllegalArgumentException("missing authData");
            }
            authDataBytes = authData.binaryValue();
        } catch (Exception ex) {
            log.debug("Unreadable attestation object", ex);
            throw ApiException.badRequest("FINGERPRINT_SETUP_FAILED",
                    "Your phone's response could not be read. Please try again.");
        }

        AuthenticatorDataParser.AuthenticatorData authData = parseAuthData(authDataBytes);
        requireRpIdHash(authData.rpIdHash());

        if (!authData.userPresent()) {
            throw ApiException.badRequest("FINGERPRINT_SETUP_FAILED",
                    "Please follow the prompt on your phone to finish setting up your fingerprint.");
        }
        if (!authData.userVerified()) {
            throw ApiException.badRequest("FINGERPRINT_NOT_VERIFIED",
                    "Your phone did not confirm your fingerprint. Please make sure a screen lock and fingerprint are set up, then try again.");
        }
        if (authData.credentialId() == null || authData.credentialPublicKey() == null) {
            throw ApiException.badRequest("FINGERPRINT_SETUP_FAILED",
                    "Your phone did not send a usable credential. Please try again.");
        }

        CoseKeyParser.ParsedKey parsed;
        try {
            parsed = CoseKeyParser.parse(authData.credentialPublicKey());
        } catch (IllegalArgumentException ex) {
            log.debug("Unsupported COSE key", ex);
            throw ApiException.badRequest("FINGERPRINT_UNSUPPORTED",
                    "This phone's security hardware is not supported yet. Please ask your administrator for help.");
        }

        String credentialId = TokenUtil.base64Url(authData.credentialId());
        if (credentialRepository.findByCredentialId(credentialId).isPresent()) {
            throw ApiException.conflict("DEVICE_ALREADY_REGISTERED",
                    "This device is already registered.");
        }

        // For the MVP one employee holds one active credential. Registering a new
        // phone retires the previous one; history stays untouched.
        credentialRepository.findFirstByEmployeeIdAndStatus(employee.getId(), CredentialStatus.ACTIVE)
                .ifPresent(previous -> {
                    previous.setStatus(CredentialStatus.REVOKED);
                    previous.setRevokedAt(Instant.now());
                    previous.setRevokedReason("Replaced when a new device was registered");
                    credentialRepository.save(previous);
                });
        credentialRepository.flush();

        DeviceCredential credential = new DeviceCredential();
        credential.setEmployee(employee);
        credential.setCredentialId(credentialId);
        credential.setPublicKey(Base64.getEncoder().encodeToString(parsed.publicKey().getEncoded()));
        credential.setAlgorithm(parsed.algorithmName());
        credential.setSignCount(authData.signCount());
        credential.setAaguid(AuthenticatorDataParser.formatAaguid(authData.aaguid()));
        credential.setDeviceLabel(sanitiseLabel(request.deviceLabel()));
        credential.setStatus(CredentialStatus.ACTIVE);

        return credentialRepository.save(credential);
    }

    // -------------------------------------------------------------------------
    // Authentication (used to verify an individual work event)
    // -------------------------------------------------------------------------

    @Transactional
    public AuthenticationOptions startAuthentication(UserAccount user, Employee employee) {
        DeviceCredential credential = credentialRepository
                .findFirstByEmployeeIdAndStatus(employee.getId(), CredentialStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.PRECONDITION_REQUIRED, "NO_REGISTERED_DEVICE",
                        "This phone is not set up for fingerprint yet."));

        String challenge = issueChallenge(user, ChallengePurpose.AUTHENTICATION);
        return new AuthenticationOptions(
                challenge,
                properties.getWebauthn().getRpId(),
                120_000L,
                "required",
                List.of(Map.of("type", "public-key", "id", credential.getCredentialId())));
    }

    /**
     * Verifies an assertion produced by the employee's phone.
     *
     * <p>Returns a result rather than throwing, because a failed fingerprint
     * check does not always mean the event should be thrown away: an event
     * captured offline is still recorded, just not marked as verified.
     */
    @Transactional
    public VerificationResult verifyAssertion(UserAccount user, Employee employee, DeviceAssertion assertion) {
        if (assertion == null || !assertion.isComplete()) {
            return VerificationResult.failed("No fingerprint check was provided");
        }

        Optional<DeviceCredential> found = credentialRepository.findByCredentialId(assertion.credentialId());
        if (found.isEmpty()) {
            return VerificationResult.failed("This device is not registered");
        }
        DeviceCredential credential = found.get();

        if (!credential.getEmployee().getId().equals(employee.getId())) {
            return VerificationResult.failed("This device belongs to a different employee");
        }
        if (!credential.isActive()) {
            return VerificationResult.failed("This device's registration has been revoked");
        }

        ClientData clientData;
        try {
            clientData = readClientData(assertion.clientDataJSON());
        } catch (ApiException ex) {
            return VerificationResult.failed("The device response could not be read");
        }
        if (!"webauthn.get".equals(clientData.type())) {
            return VerificationResult.failed("Unexpected device response");
        }
        if (!properties.getWebauthn().originList().contains(clientData.origin())) {
            return VerificationResult.failed("The device response came from an unexpected address");
        }

        try {
            consumeChallenge(user, clientData.challenge(), ChallengePurpose.AUTHENTICATION);
        } catch (ApiException ex) {
            return VerificationResult.failed("The fingerprint check expired. Please try again.");
        }

        byte[] authDataBytes = decode(assertion.authenticatorData(), "authenticatorData");
        AuthenticatorDataParser.AuthenticatorData authData;
        try {
            authData = AuthenticatorDataParser.parse(authDataBytes);
        } catch (IllegalArgumentException ex) {
            return VerificationResult.failed("The device response was incomplete");
        }

        if (!MessageDigest.isEqual(authData.rpIdHash(), rpIdHash())) {
            return VerificationResult.failed("The device response was issued for a different site");
        }
        if (!authData.userPresent()) {
            return VerificationResult.failed("The fingerprint prompt was not completed");
        }
        if (!authData.userVerified()) {
            return VerificationResult.failed("Your phone did not confirm your fingerprint");
        }

        boolean signatureValid;
        try {
            byte[] clientDataHash = TokenUtil.sha256Bytes(
                    TokenUtil.fromBase64Url(assertion.clientDataJSON()));
            byte[] signed = new byte[authDataBytes.length + clientDataHash.length];
            System.arraycopy(authDataBytes, 0, signed, 0, authDataBytes.length);
            System.arraycopy(clientDataHash, 0, signed, authDataBytes.length, clientDataHash.length);

            PublicKey publicKey = KeyFactory
                    .getInstance(CoseKeyParser.keyFactoryAlgorithm(credential.getAlgorithm()))
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(credential.getPublicKey())));

            Signature verifier = Signature.getInstance(
                    CoseKeyParser.jcaSignatureAlgorithm(credential.getAlgorithm()));
            verifier.initVerify(publicKey);
            verifier.update(signed);
            signatureValid = verifier.verify(decode(assertion.signature(), "signature"));
        } catch (Exception ex) {
            log.debug("Assertion signature verification failed", ex);
            return VerificationResult.failed("The fingerprint check could not be verified");
        }

        if (!signatureValid) {
            return VerificationResult.failed("The fingerprint check could not be verified");
        }

        // A signature counter that fails to advance can indicate a cloned
        // authenticator. Many platform authenticators legitimately report 0
        // always, so this is a flag, never a rejection.
        boolean signCountAnomaly = authData.signCount() != 0
                && credential.getSignCount() != 0
                && authData.signCount() <= credential.getSignCount();

        credential.setSignCount(Math.max(credential.getSignCount(), authData.signCount()));
        credential.setLastUsedAt(Instant.now());
        credentialRepository.save(credential);

        return VerificationResult.ok(credential.getCredentialId(), signCountAnomaly);
    }

    public Optional<DeviceCredential> activeCredential(Employee employee) {
        return credentialRepository.findFirstByEmployeeIdAndStatus(employee.getId(), CredentialStatus.ACTIVE);
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    private String issueChallenge(UserAccount user, ChallengePurpose purpose) {
        String challenge = TokenUtil.base64Url(TokenUtil.randomBytes(32));

        WebAuthnChallenge entity = new WebAuthnChallenge();
        entity.setUser(user);
        entity.setChallenge(challenge);
        entity.setPurpose(purpose);
        entity.setExpiresAt(Instant.now().plusSeconds(properties.getWebauthn().getChallengeTtlSeconds()));
        challengeRepository.save(entity);

        return challenge;
    }

    private void consumeChallenge(UserAccount user, String challenge, ChallengePurpose purpose) {
        WebAuthnChallenge stored = challengeRepository.findByChallenge(challenge)
                .orElseThrow(() -> ApiException.badRequest("CHALLENGE_INVALID",
                        "That security check has expired. Please try again."));

        if (!stored.isUsable() || stored.getPurpose() != purpose
                || !stored.getUser().getId().equals(user.getId())) {
            throw ApiException.badRequest("CHALLENGE_INVALID",
                    "That security check has expired. Please try again.");
        }

        stored.setConsumed(true);
        challengeRepository.save(stored);
    }

    private ClientData readClientData(String base64Url) {
        try {
            JsonNode node = JSON.readTree(new String(decode(base64Url, "clientDataJSON"), StandardCharsets.UTF_8));
            return new ClientData(
                    node.path("type").asText(null),
                    node.path("challenge").asText(null),
                    node.path("origin").asText(null));
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw ApiException.badRequest("DEVICE_RESPONSE_INVALID",
                    "Your phone's response could not be read. Please try again.");
        }
    }

    private void requireType(ClientData clientData, String expected) {
        if (!expected.equals(clientData.type())) {
            throw ApiException.badRequest("DEVICE_RESPONSE_INVALID",
                    "Your phone's response could not be read. Please try again.");
        }
    }

    private void requireAllowedOrigin(String origin) {
        if (origin == null || !properties.getWebauthn().originList().contains(origin)) {
            throw ApiException.badRequest("ORIGIN_NOT_ALLOWED",
                    "Please open Trackr from the official link and try again.");
        }
    }

    private void requireRpIdHash(byte[] actual) {
        if (!MessageDigest.isEqual(actual, rpIdHash())) {
            throw ApiException.badRequest("ORIGIN_NOT_ALLOWED",
                    "Please open Trackr from the official link and try again.");
        }
    }

    private byte[] rpIdHash() {
        return TokenUtil.sha256Bytes(properties.getWebauthn().getRpId().getBytes(StandardCharsets.UTF_8));
    }

    private AuthenticatorDataParser.AuthenticatorData parseAuthData(byte[] bytes) {
        try {
            return AuthenticatorDataParser.parse(bytes);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("DEVICE_RESPONSE_INVALID",
                    "Your phone's response was incomplete. Please try again.");
        }
    }

    private byte[] decode(String value, String label) {
        try {
            return TokenUtil.fromBase64Url(value);
        } catch (IllegalArgumentException ex) {
            log.debug("Could not decode {}", label, ex);
            throw ApiException.badRequest("DEVICE_RESPONSE_INVALID",
                    "Your phone's response could not be read. Please try again.");
        }
    }

    private static String sanitiseLabel(String label) {
        if (label == null || label.isBlank()) {
            return "Registered phone";
        }
        String trimmed = label.trim();
        return trimmed.length() > 120 ? trimmed.substring(0, 120) : trimmed;
    }

    private record ClientData(String type, String challenge, String origin) {
    }
}
