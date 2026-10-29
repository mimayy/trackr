package com.uniace.trackr.repo;

import com.uniace.trackr.domain.Employee;
import com.uniace.trackr.domain.enums.AccountStatus;
import com.uniace.trackr.domain.enums.Branch;
import com.uniace.trackr.domain.enums.WorkCategory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    Optional<Employee> findByUserId(UUID userId);

    Optional<Employee> findByEmployeeNoIgnoreCase(String employeeNo);

    Optional<Employee> findByEmailIgnoreCase(String email);

    boolean existsByEmployeeNoIgnoreCase(String employeeNo);

    boolean existsByEmailIgnoreCase(String email);

    List<Employee> findBySupervisorId(UUID supervisorId);

    List<Employee> findByAccountStatus(AccountStatus status);

    long countByAccountStatus(AccountStatus status);

    @Query("""
            select e from Employee e
              left join fetch e.supervisor s
              left join fetch e.workSchedule w
              left join fetch e.user u
            where (:branch is null or e.branch = :branch)
              and (:category is null or e.workCategory = :category)
              and (:status is null or e.accountStatus = :status)
              and (:supervisorId is null or s.id = :supervisorId)
            order by e.lastName asc, e.firstName asc
            """)
    List<Employee> search(@Param("branch") Branch branch,
                          @Param("category") WorkCategory category,
                          @Param("status") AccountStatus status,
                          @Param("supervisorId") UUID supervisorId);

    @Query("""
            select e from Employee e
              left join fetch e.supervisor
              left join fetch e.workSchedule
              left join fetch e.user
              left join fetch e.homeOffice
            where e.id = :id
            """)
    Optional<Employee> findDetailedById(@Param("id") UUID id);

    /**
     * The employee behind a login, with the associations the caller is certain
     * to touch already loaded.
     *
     * <p>The work schedule in particular: attendance is calculated outside any
     * transaction in places, and a lazy proxy would fail there rather than
     * quietly costing an extra query.
     */
    @Query("""
            select e from Employee e
              left join fetch e.supervisor
              left join fetch e.workSchedule
              left join fetch e.user
              left join fetch e.homeOffice
            where e.user.id = :userId
            """)
    Optional<Employee> findDetailedByUserId(@Param("userId") UUID userId);
}
