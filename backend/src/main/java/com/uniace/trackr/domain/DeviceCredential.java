package com.uniace.trackr.domain;

import com.uniace.trackr.domain.enums.CredentialStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * A registered device credential (WebAuthn / passkey).
 *
 * <p>No biometric data of any kind is stored here. The employee's fingerprint
 * never leaves their phone: the device unlocks a private key, and Trackr only
 * keeps the matching public key so it can verify signatures.
 */
@Entity
@Table(name = "device_credentials")
public class DeviceCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    /** Base64url credential id issued by the authenticator. */
    @Column(name = "credential_id", nullable = false, unique = true, length = 500)
    private String credentialId;

    @Column(name = "credential_type", nullable = false, length = 40)
    private String credentialType = "PUBLIC_KEY";

    /** Base64 encoded X.509 SubjectPublicKeyInfo. */
    @Column(name = "public_key", nullable = false, columnDefinition = "TEXT")
    private String publicKey;

    /** COSE algorithm name, e.g. {@code ES256} or {@code RS256}. */
    @Column(nullable = false, length = 20)
    private String algorithm;

    @Column(name = "sign_count", nullable = false)
    private long signCount;

    @Column(length = 60)
    private String aaguid;

    /** Friendly label the employee sees, e.g. "iPhone 15". */
    @Column(name = "device_label", length = 120)
    private String deviceLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CredentialStatus status = CredentialStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revoked_by")
    private UserAccount revokedBy;

    @Column(name = "revoked_reason", length = 300)
    private String revokedReason;

    public boolean isActive() {
        return status == CredentialStatus.ACTIVE;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }

    public String getCredentialId() { return credentialId; }
    public void setCredentialId(String credentialId) { this.credentialId = credentialId; }

    public String getCredentialType() { return credentialType; }
    public void setCredentialType(String credentialType) { this.credentialType = credentialType; }

    public String getPublicKey() { return publicKey; }
    public void setPublicKey(String publicKey) { this.publicKey = publicKey; }

    public String getAlgorithm() { return algorithm; }
    public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }

    public long getSignCount() { return signCount; }
    public void setSignCount(long signCount) { this.signCount = signCount; }

    public String getAaguid() { return aaguid; }
    public void setAaguid(String aaguid) { this.aaguid = aaguid; }

    public String getDeviceLabel() { return deviceLabel; }
    public void setDeviceLabel(String deviceLabel) { this.deviceLabel = deviceLabel; }

    public CredentialStatus getStatus() { return status; }
    public void setStatus(CredentialStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; }

    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }

    public UserAccount getRevokedBy() { return revokedBy; }
    public void setRevokedBy(UserAccount revokedBy) { this.revokedBy = revokedBy; }

    public String getRevokedReason() { return revokedReason; }
    public void setRevokedReason(String revokedReason) { this.revokedReason = revokedReason; }
}
