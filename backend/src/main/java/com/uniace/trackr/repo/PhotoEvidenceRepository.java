package com.uniace.trackr.repo;

import com.uniace.trackr.domain.PhotoEvidence;
import com.uniace.trackr.domain.enums.PhotoStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhotoEvidenceRepository extends JpaRepository<PhotoEvidence, UUID> {

    List<PhotoEvidence> findByStatusAndCreatedAtBefore(PhotoStatus status, Instant cutoff);

    long countByEmployeeId(UUID employeeId);
}
