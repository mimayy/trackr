package com.uniace.trackr.repo;

import com.uniace.trackr.domain.ActivationToken;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivationTokenRepository extends JpaRepository<ActivationToken, UUID> {

    Optional<ActivationToken> findByTokenHash(String tokenHash);

    List<ActivationToken> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
