package com.uniace.trackr.seed;

import com.uniace.trackr.config.TrackrProperties;
import com.uniace.trackr.domain.UserAccount;
import com.uniace.trackr.domain.enums.AccountStatus;
import com.uniace.trackr.domain.enums.AuditResult;
import com.uniace.trackr.domain.enums.Role;
import com.uniace.trackr.repo.UserAccountRepository;
import com.uniace.trackr.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first administrator so the system can be used at all.
 *
 * <p>The account is created from environment variables and only when no
 * administrator exists yet. The password is hashed immediately - it is never
 * written to the database in the clear, and never committed to source, which is
 * why no user accounts are seeded in SQL.
 */
@Component
@Order(1)
public class BootstrapService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapService.class);

    private final UserAccountRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TrackrProperties properties;
    private final AuditService auditService;

    public BootstrapService(UserAccountRepository userRepository,
                            PasswordEncoder passwordEncoder,
                            TrackrProperties properties,
                            AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.countByRole(Role.ADMIN) > 0) {
            return;
        }

        String email = properties.getSecurity().getBootstrapAdminEmail().trim().toLowerCase();
        String password = properties.getSecurity().getBootstrapAdminPassword();

        if (userRepository.existsByEmailIgnoreCase(email)) {
            log.warn("Bootstrap administrator email {} is already taken by another account; skipping.", email);
            return;
        }

        UserAccount admin = new UserAccount();
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRole(Role.ADMIN);
        admin.setStatus(AccountStatus.ACTIVE);
        admin.setDisplayName("Efren P. Casingal");
        userRepository.save(admin);

        auditService.recordSystem("BOOTSTRAP_ADMIN", "UserAccount", admin.getId().toString(),
                AuditResult.SUCCESS, "First administrator account created from environment configuration");

        log.info("Created the first administrator account: {}", email);
        if ("ChangeMe123!".equals(password)) {
            log.warn("The administrator is using the default bootstrap password. "
                    + "Set BOOTSTRAP_ADMIN_PASSWORD and change it before deploying.");
        }
    }
}
