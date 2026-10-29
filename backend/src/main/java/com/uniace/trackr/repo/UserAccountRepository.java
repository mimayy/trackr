package com.uniace.trackr.repo;

import com.uniace.trackr.domain.UserAccount;
import com.uniace.trackr.domain.enums.Role;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    List<UserAccount> findByRole(Role role);

    long countByRole(Role role);
}
