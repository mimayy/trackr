package com.uniace.trackr.domain;

import com.uniace.trackr.domain.enums.ChallengePurpose;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * A short-lived, single-use WebAuthn challenge. Storing it server side is what
 * stops an assertion captured from one session being replayed in another.
 */
@Entity
@Table(name = "webauthn_challenges")
public class WebAuthnChallenge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    /** Base64url encoded random challenge. */
    @Column(nullable = false, unique = true, length = 200)
    private String challenge;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ChallengePurpose purpose;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean consumed;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public boolean isUsable() {
        return !consumed && expiresAt.isAfter(Instant.now());
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UserAccount getUser() { return user; }
    public void setUser(UserAccount user) { this.user = user; }

    public String getChallenge() { return challenge; }
    public void setChallenge(String challenge) { this.challenge = challenge; }

    public ChallengePurpose getPurpose() { return purpose; }
    public void setPurpose(ChallengePurpose purpose) { this.purpose = purpose; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public boolean isConsumed() { return consumed; }
    public void setConsumed(boolean consumed) { this.consumed = consumed; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
