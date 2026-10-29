package com.uniace.trackr.service;

import com.uniace.trackr.config.TrackrProperties;
import com.uniace.trackr.domain.ActivationToken;
import com.uniace.trackr.domain.Employee;
import com.uniace.trackr.domain.UserAccount;
import com.uniace.trackr.domain.enums.AccountStatus;
import com.uniace.trackr.dto.AuthDtos.ActivationDetails;
import com.uniace.trackr.dto.AuthDtos.InvitationResponse;
import com.uniace.trackr.dto.AuthDtos.SessionResponse;
import com.uniace.trackr.repo.ActivationTokenRepository;
import com.uniace.trackr.repo.EmployeeRepository;
import com.uniace.trackr.repo.UserAccountRepository;
import com.uniace.trackr.util.TokenUtil;
import com.uniace.trackr.web.error.ApiException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turning a created account into a working one.
 *
 * <p>The invitation token is random, single use, short lived and stored only as
 * a hash. Creating a new invitation invalidates any earlier one, and activating
 * invalidates them all - so a link that has been forwarded, screenshotted or
 * left in an inbox stops working the moment it has been used once.
 */
@Service
public class ActivationService {

    private final ActivationTokenRepository tokenRepository;
    private final UserAccountRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final EmailService emailService;
    private final TrackrProperties properties;

    public ActivationService(ActivationTokenRepository tokenRepository,
                             UserAccountRepository userRepository,
                             EmployeeRepository employeeRepository,
                             PasswordEncoder passwordEncoder,
                             AuthService authService,
                             EmailService emailService,
                             TrackrProperties properties) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
        this.emailService = emailService;
        this.properties = properties;
    }

    /**
     * Issues a fresh invitation. The raw token is returned exactly once, in
     * this response - it is never stored and cannot be recovered later. If the
     * administrator loses it, they generate a new one.
     */
    @Transactional
    public InvitationResponse createInvitation(UserAccount user, UserAccount createdBy, boolean sendEmail) {
        if (user.getStatus() == AccountStatus.DISABLED) {
            throw ApiException.badRequest("ACCOUNT_DISABLED",
                    "This account is disabled. Enable it before sending an invitation.");
        }

        invalidateExisting(user);

        String raw = TokenUtil.randomToken();
        long hours = properties.getActivation().getTokenHours();
        Instant expiresAt = Instant.now().plus(Duration.ofHours(hours));

        ActivationToken token = new ActivationToken();
        token.setUser(user);
        token.setTokenHash(TokenUtil.sha256(raw));
        token.setExpiresAt(expiresAt);
        token.setCreatedBy(createdBy);
        tokenRepository.save(token);

        String url = properties.getAppBaseUrl().replaceAll("/+$", "")
                + "/activate?token=" + URLEncoder.encode(raw, StandardCharsets.UTF_8);

        EmailService.SendOutcome outcome = sendEmail
                ? emailService.sendInvitation(user.getEmail(), firstNameOf(user), url, hours)
                : new EmailService.SendOutcome(false, null);

        return new InvitationResponse(url, expiresAt, outcome.sent(), outcome.note());
    }

    /** Shows the employee who they are before they set a password. */
    @Transactional(readOnly = true)
    public ActivationDetails describe(String rawToken) {
        ActivationToken token = requireUsable(rawToken);
        UserAccount user = token.getUser();
        Employee employee = employeeRepository.findByUserId(user.getId()).orElse(null);

        return new ActivationDetails(
                firstNameOf(user),
                user.getDisplayName(),
                employee == null ? null : employee.getEmployeeNo(),
                user.getEmail(),
                employee == null ? null : employee.getBranch(),
                employee == null ? null : employee.getWorkCategory(),
                employee == null || employee.getSupervisor() == null ? null : employee.getSupervisor().fullName(),
                employee == null || employee.getWorkSchedule() == null ? null : employee.getWorkSchedule().getName(),
                token.getExpiresAt());
    }

    /**
     * Completes activation and signs the employee straight in, so they go from
     * the link to registering their fingerprint without a second login screen.
     */
    @Transactional
    public SessionResponse activate(String rawToken, String password) {
        ActivationToken token = requireUsable(rawToken);
        UserAccount user = token.getUser();

        user.setPasswordHash(passwordEncoder.encode(password));
        user.setStatus(AccountStatus.ACTIVE);
        user.setFailedLogins(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        employeeRepository.findByUserId(user.getId()).ifPresent(employee -> {
            employee.setAccountStatus(AccountStatus.ACTIVE);
            employeeRepository.save(employee);
        });

        token.setUsedAt(Instant.now());
        tokenRepository.save(token);
        invalidateExisting(user);

        return authService.issueSession(user);
    }

    private ActivationToken requireUsable(String rawToken) {
        ActivationToken token = tokenRepository.findByTokenHash(TokenUtil.sha256(rawToken))
                .orElseThrow(() -> ApiException.badRequest("ACTIVATION_INVALID",
                        "This activation link is not valid. Please ask your administrator for a new one."));

        if (token.getUsedAt() != null) {
            throw ApiException.badRequest("ACTIVATION_USED",
                    "This activation link has already been used. Please log in instead.");
        }
        if (!token.isUsable()) {
            throw ApiException.badRequest("ACTIVATION_EXPIRED",
                    "This activation link has expired. Please ask your administrator for a new one.");
        }
        return token;
    }

    private void invalidateExisting(UserAccount user) {
        List<ActivationToken> existing = tokenRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        existing.stream()
                .filter(t -> t.getUsedAt() == null && !t.isInvalidated())
                .forEach(t -> t.setInvalidated(true));
        tokenRepository.saveAll(existing);
    }

    private static String firstNameOf(UserAccount user) {
        String name = user.getDisplayName();
        if (name == null || name.isBlank()) {
            return "there";
        }
        return name.trim().split("\\s+")[0];
    }
}
