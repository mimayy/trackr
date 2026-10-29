package com.uniace.trackr.service;

import com.uniace.trackr.config.TrackrProperties;
import com.uniace.trackr.domain.*;
import com.uniace.trackr.domain.enums.AccountStatus;
import com.uniace.trackr.dto.AuthDtos.LoginRequest;
import com.uniace.trackr.dto.AuthDtos.SessionResponse;
import com.uniace.trackr.dto.AuthDtos.SessionUser;
import com.uniace.trackr.repo.*;
import com.uniace.trackr.security.AuthPrincipal;
import com.uniace.trackr.security.JwtService;
import com.uniace.trackr.util.TokenUtil;
import com.uniace.trackr.web.error.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sign in, refresh and sign out.
 *
 * <p>Login failures always report the same thing whether the email is unknown
 * or the password is wrong, so the endpoint cannot be used to discover who
 * works at Uni-Ace. Repeated failures lock the account briefly; the rate limit
 * filter caps attempts per address on top of that.
 */
@Service
public class AuthService {

    private static final int MAX_FAILED_LOGINS = 8;
    private static final Duration LOCKOUT = Duration.ofMinutes(15);

    /**
     * A well-formed BCrypt hash of a value nobody knows, compared against when
     * the email does not exist. It is a decoy, not a credential: its only job is
     * to make "no such account" cost the same time as "wrong password".
     */
    private static final String DECOY_HASH = "$2a$12$C6UzMDM.H6dfI/f/IKcEe.7VmMh0d3S8bV5nQ0mWlG5PdlHRbjfPy";

    private final UserAccountRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final SupervisorRepository supervisorRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final DeviceCredentialRepository credentialRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final TrackrProperties properties;

    public AuthService(UserAccountRepository userRepository,
                       EmployeeRepository employeeRepository,
                       SupervisorRepository supervisorRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       DeviceCredentialRepository credentialRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuditService auditService,
                       TrackrProperties properties) {
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.supervisorRepository = supervisorRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.credentialRepository = credentialRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
        this.properties = properties;
    }

    @Transactional
    public SessionResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        Optional<UserAccount> found = userRepository.findByEmailIgnoreCase(email);

        if (found.isEmpty()) {
            // Still hash something so a missing account and a wrong password
            // take about the same time to answer.
            passwordEncoder.matches(request.password(), DECOY_HASH);
            auditService.recordLogin(null, false, email, "No account with that email");
            throw ApiException.unauthorized("The email or password is incorrect.");
        }

        UserAccount user = found.get();

        if (user.isLocked()) {
            auditService.recordLogin(user, false, email, "Account temporarily locked");
            throw ApiException.unauthorized(
                    "Too many attempts. Please wait a few minutes and try again.");
        }

        if (user.getStatus() == AccountStatus.DISABLED) {
            auditService.recordLogin(user, false, email, "Account disabled");
            throw ApiException.unauthorized(
                    "This account has been disabled. Please contact your administrator.");
        }

        if (user.getStatus() == AccountStatus.PENDING_ACTIVATION || user.getPasswordHash() == null) {
            auditService.recordLogin(user, false, email, "Account not activated");
            throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "NOT_ACTIVATED",
                    "This account has not been activated yet. Please open the activation link your administrator sent you.");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            user.setFailedLogins(user.getFailedLogins() + 1);
            if (user.getFailedLogins() >= MAX_FAILED_LOGINS) {
                user.setLockedUntil(Instant.now().plus(LOCKOUT));
                user.setFailedLogins(0);
            }
            userRepository.save(user);
            auditService.recordLogin(user, false, email, "Incorrect password");
            throw ApiException.unauthorized("The email or password is incorrect.");
        }

        user.setFailedLogins(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
        auditService.recordLogin(user, true, email, null);

        return issueSession(user);
    }

    @Transactional
    public SessionResponse refresh(String rawRefreshToken) {
        String hash = TokenUtil.sha256(rawRefreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> ApiException.unauthorized(
                        "We could not verify your session. Please log in again."));

        if (!stored.isUsable()) {
            throw ApiException.unauthorized("Your session has expired. Please log in again.");
        }

        UserAccount user = stored.getUser();
        if (user.getStatus() != AccountStatus.ACTIVE) {
            throw ApiException.unauthorized("This account is no longer active. Please contact your administrator.");
        }

        // Rotate: the presented token is retired as the new one is issued, so a
        // stolen refresh token stops working the moment the real user refreshes.
        stored.setRevokedAt(Instant.now());
        refreshTokenRepository.save(stored);

        return issueSession(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(TokenUtil.sha256(rawRefreshToken)).ifPresent(token -> {
            token.setRevokedAt(Instant.now());
            refreshTokenRepository.save(token);
        });
    }

    @Transactional
    public void revokeAllSessions(UserAccount user) {
        refreshTokenRepository.revokeAllForUser(user.getId(), Instant.now());
    }

    @Transactional
    public SessionResponse issueSession(UserAccount user) {
        AuthPrincipal principal = principalFor(user);

        String rawRefresh = TokenUtil.randomToken();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(TokenUtil.sha256(rawRefresh));
        refreshToken.setExpiresAt(Instant.now().plus(
                Duration.ofDays(properties.getSecurity().getRefreshTokenDays())));
        refreshTokenRepository.save(refreshToken);

        return new SessionResponse(
                jwtService.issueAccessToken(principal),
                rawRefresh,
                jwtService.accessTokenLifetime().toSeconds(),
                sessionUser(user));
    }

    @Transactional(readOnly = true)
    public AuthPrincipal principalFor(UserAccount user) {
        UUIDHolder holder = profileIds(user);
        return new AuthPrincipal(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRole(),
                holder.employeeId,
                holder.supervisorId);
    }

    @Transactional(readOnly = true)
    public SessionUser sessionUser(UserAccount user) {
        // Supervisors and administrators normally have no employee profile;
        // when they do (a supervisor who also records their own attendance) it
        // is used, so the answer is simply "whatever profile this login has".
        Employee employee = employeeRepository.findByUserId(user.getId()).orElse(null);

        DeviceCredential credential = employee == null ? null
                : credentialRepository.findFirstByEmployeeIdAndStatus(
                        employee.getId(), com.uniace.trackr.domain.enums.CredentialStatus.ACTIVE).orElse(null);

        String firstName = employee != null
                ? employee.getFirstName()
                : supervisorRepository.findByUserId(user.getId())
                        .map(Supervisor::getFirstName)
                        .orElse(firstWord(user.getDisplayName()));

        return new SessionUser(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                firstName,
                user.getRole(),
                user.getStatus(),
                employee == null ? null : employee.getId(),
                employee == null ? null : employee.getEmployeeNo(),
                employee == null ? null : employee.getBranch(),
                employee == null ? null : employee.getWorkCategory(),
                employee == null || employee.getSupervisor() == null ? null : employee.getSupervisor().fullName(),
                employee == null || employee.getWorkSchedule() == null ? null : employee.getWorkSchedule().getName(),
                employee == null || employee.getHomeOffice() == null ? null : employee.getHomeOffice().getName(),
                credential != null,
                credential == null ? null : credential.getDeviceLabel());
    }

    private UUIDHolder profileIds(UserAccount user) {
        UUIDHolder holder = new UUIDHolder();
        employeeRepository.findByUserId(user.getId()).ifPresent(e -> holder.employeeId = e.getId());
        supervisorRepository.findByUserId(user.getId()).ifPresent(s -> holder.supervisorId = s.getId());
        return holder;
    }

    private static String firstWord(String value) {
        if (value == null || value.isBlank()) {
            return "there";
        }
        return value.trim().split("\\s+")[0];
    }

    private static final class UUIDHolder {
        java.util.UUID employeeId;
        java.util.UUID supervisorId;
    }
}
