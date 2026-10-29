package com.uniace.trackr.repo;

import com.uniace.trackr.domain.DeviceCredential;
import com.uniace.trackr.domain.enums.CredentialStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceCredentialRepository extends JpaRepository<DeviceCredential, UUID> {

    Optional<DeviceCredential> findByCredentialId(String credentialId);

    Optional<DeviceCredential> findFirstByEmployeeIdAndStatus(UUID employeeId, CredentialStatus status);

    List<DeviceCredential> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);

    long countByStatus(CredentialStatus status);
}
