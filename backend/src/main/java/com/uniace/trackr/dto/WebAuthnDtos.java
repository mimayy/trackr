package com.uniace.trackr.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

/**
 * Request and response shapes for WebAuthn, matching the JSON the browser's
 * {@code navigator.credentials} API produces (with binary fields base64url
 * encoded by the front end).
 */
public final class WebAuthnDtos {

    private WebAuthnDtos() {
    }

    /** Options handed to {@code navigator.credentials.create()}. */
    public record RegistrationOptions(
            Map<String, String> rp,
            Map<String, String> user,
            String challenge,
            List<Map<String, Object>> pubKeyCredParams,
            long timeout,
            String attestation,
            Map<String, Object> authenticatorSelection,
            List<Map<String, Object>> excludeCredentials
    ) {
    }

    /** Options handed to {@code navigator.credentials.get()}. */
    public record AuthenticationOptions(
            String challenge,
            String rpId,
            long timeout,
            String userVerification,
            List<Map<String, Object>> allowCredentials
    ) {
    }

    public record AttestationResponse(
            @NotBlank String clientDataJSON,
            @NotBlank String attestationObject
    ) {
    }

    public record RegistrationRequest(
            @NotBlank String id,
            String type,
            AttestationResponse response,
            String deviceLabel
    ) {
    }

    public record AssertionResponse(
            @NotBlank String clientDataJSON,
            @NotBlank String authenticatorData,
            @NotBlank String signature,
            String userHandle
    ) {
    }

    public record AssertionRequest(
            @NotBlank String id,
            String type,
            AssertionResponse response
    ) {
    }

    /** What the front end sends alongside a work event to prove the fingerprint check happened. */
    public record DeviceAssertion(
            String credentialId,
            String clientDataJSON,
            String authenticatorData,
            String signature
    ) {
        public boolean isComplete() {
            return credentialId != null && !credentialId.isBlank()
                    && clientDataJSON != null && !clientDataJSON.isBlank()
                    && authenticatorData != null && !authenticatorData.isBlank()
                    && signature != null && !signature.isBlank();
        }
    }

    /** Outcome of verifying an assertion. */
    public record VerificationResult(
            boolean verified,
            String credentialIdBase64,
            String failureReason,
            boolean signCountAnomaly
    ) {
        public static VerificationResult ok(String credentialId, boolean signCountAnomaly) {
            return new VerificationResult(true, credentialId, null, signCountAnomaly);
        }

        public static VerificationResult failed(String reason) {
            return new VerificationResult(false, null, reason, false);
        }
    }
}
