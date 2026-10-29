package com.uniace.trackr.service;

import com.uniace.trackr.domain.AuditLog;
import com.uniace.trackr.domain.UserAccount;
import com.uniace.trackr.domain.enums.AuditResult;
import com.uniace.trackr.repo.AuditLogRepository;
import com.uniace.trackr.repo.UserAccountRepository;
import com.uniace.trackr.security.AuthPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Writes the audit trail.
 *
 * <p>Entries are written in their own transaction, so an audit record survives
 * even when the operation it describes is rolled back - a denied or failed
 * attempt is exactly the thing an auditor wants to see.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;
    private final UserAccountRepository userAccountRepository;

    public AuditService(AuditLogRepository auditLogRepository, UserAccountRepository userAccountRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuthPrincipal actor,
                       String action,
                       String targetType,
                       String targetId,
                       String targetLabel,
                       AuditResult result,
                       String detail) {
        try {
            AuditLog entry = new AuditLog();
            if (actor != null) {
                userAccountRepository.findById(actor.userId()).ifPresent(entry::setActor);
                entry.setActorLabel(actor.displayName() == null ? actor.email() : actor.displayName());
                entry.setActorRole(actor.role().name());
            } else {
                entry.setActorLabel("System");
                entry.setActorRole("SYSTEM");
            }
            entry.setAction(action);
            entry.setTargetType(targetType);
            entry.setTargetId(targetId);
            entry.setTargetLabel(targetLabel);
            entry.setResult(result);
            entry.setDetail(truncate(detail, 2000));
            entry.setIpAddress(currentIp());
            auditLogRepository.save(entry);
        } catch (RuntimeException ex) {
            // Auditing must never be the reason a legitimate action fails.
            log.error("Could not write audit entry for action {}", action, ex);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSystem(String action, String targetType, String targetId, AuditResult result, String detail) {
        record(null, action, targetType, targetId, null, result, detail);
    }

    public void success(AuthPrincipal actor, String action, String targetType, UUID targetId, String targetLabel, String detail) {
        record(actor, action, targetType, targetId == null ? null : targetId.toString(), targetLabel, AuditResult.SUCCESS, detail);
    }

    public void failure(AuthPrincipal actor, String action, String targetType, String targetId, String detail) {
        record(actor, action, targetType, targetId, null, AuditResult.FAILURE, detail);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLogin(UserAccount user, boolean succeeded, String email, String detail) {
        try {
            AuditLog entry = new AuditLog();
            entry.setActor(user);
            entry.setActorLabel(user != null ? user.getDisplayName() : email);
            entry.setActorRole(user != null ? user.getRole().name() : "SYSTEM");
            entry.setAction(succeeded ? "LOGIN" : "LOGIN_FAILED");
            entry.setTargetType("UserAccount");
            entry.setTargetId(user == null ? null : user.getId().toString());
            entry.setTargetLabel(email);
            entry.setResult(succeeded ? AuditResult.SUCCESS : AuditResult.FAILURE);
            entry.setDetail(truncate(detail, 2000));
            entry.setIpAddress(currentIp());
            auditLogRepository.save(entry);
        } catch (RuntimeException ex) {
            log.error("Could not write login audit entry", ex);
        }
    }

    private static String currentIp() {
        try {
            var attributes = RequestContextHolder.getRequestAttributes();
            if (attributes instanceof ServletRequestAttributes servletAttributes) {
                HttpServletRequest request = servletAttributes.getRequest();
                String forwarded = request.getHeader("X-Forwarded-For");
                if (forwarded != null && !forwarded.isBlank()) {
                    return truncate(forwarded.split(",")[0].trim(), 60);
                }
                return truncate(request.getRemoteAddr(), 60);
            }
        } catch (RuntimeException ignored) {
            // Background jobs have no request; that is fine.
        }
        return null;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
