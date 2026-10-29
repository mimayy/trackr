package com.uniace.trackr.repo;

import com.uniace.trackr.domain.Supervisor;
import com.uniace.trackr.domain.enums.Branch;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupervisorRepository extends JpaRepository<Supervisor, UUID> {

    Optional<Supervisor> findByUserId(UUID userId);

    Optional<Supervisor> findByEmailIgnoreCase(String email);

    List<Supervisor> findByBranch(Branch branch);

    /**
     * The supervisor list, with each one's login account already loaded - the
     * list shows account status for every row, so fetching it lazily would be a
     * query per supervisor.
     */
    @Query("""
            select s from Supervisor s
              join fetch s.user
            order by s.lastName asc, s.firstName asc
            """)
    List<Supervisor> findAllByOrderByLastNameAscFirstNameAsc();

    /**
     * One supervisor with their login account loaded, for callers that act on
     * the account outside the loading transaction (issuing an invitation, for
     * example).
     */
    @Query("""
            select s from Supervisor s
              join fetch s.user
            where s.id = :id
            """)
    Optional<Supervisor> findDetailedById(@Param("id") UUID id);

    boolean existsByEmailIgnoreCase(String email);
}
