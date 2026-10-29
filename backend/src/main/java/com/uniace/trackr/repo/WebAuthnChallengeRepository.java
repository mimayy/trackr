package com.uniace.trackr.repo;

import com.uniace.trackr.domain.WebAuthnChallenge;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WebAuthnChallengeRepository extends JpaRepository<WebAuthnChallenge, UUID> {

    Optional<WebAuthnChallenge> findByChallenge(String challenge);

    @Modifying
    @Query("delete from WebAuthnChallenge c where c.expiresAt < :cutoff")
    int deleteExpired(@Param("cutoff") Instant cutoff);
}
