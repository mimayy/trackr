package com.uniace.trackr.domain;

import com.uniace.trackr.domain.enums.AuditResult;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Append-only record of who did what. Never updated or deleted by the app. */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private UserAccount actor;

    /** Denormalised so the log stays readable even if the account is removed. */
    @Column(name = "actor_label", nullable = false, length = 160)
    private String actorLabel;

    @Column(name = "actor_role", nullable = false, length = 20)
    private String actorRole;

    @Column(nullable = false, length = 80)
    private String action;

    @Column(name = "target_type", length = 60)
    private String targetType;

    @Column(name = "target_id", length = 80)
    private String targetId;

    @Column(name = "target_label", length = 200)
    private String targetLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuditResult result = AuditResult.SUCCESS;

    @Column(length = 2000)
    private String detail;

    @Column(name = "ip_address", length = 60)
    private String ipAddress;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }

    public UserAccount getActor() { return actor; }
    public void setActor(UserAccount actor) { this.actor = actor; }

    public String getActorLabel() { return actorLabel; }
    public void setActorLabel(String actorLabel) { this.actorLabel = actorLabel; }

    public String getActorRole() { return actorRole; }
    public void setActorRole(String actorRole) { this.actorRole = actorRole; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }

    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }

    public String getTargetLabel() { return targetLabel; }
    public void setTargetLabel(String targetLabel) { this.targetLabel = targetLabel; }

    public AuditResult getResult() { return result; }
    public void setResult(AuditResult result) { this.result = result; }

    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
}
