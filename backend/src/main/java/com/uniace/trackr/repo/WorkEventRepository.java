package com.uniace.trackr.repo;

import com.uniace.trackr.domain.WorkEvent;
import com.uniace.trackr.domain.enums.Branch;
import com.uniace.trackr.domain.enums.EventType;
import com.uniace.trackr.domain.enums.ReviewStatus;
import com.uniace.trackr.domain.enums.WorkCategory;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkEventRepository extends JpaRepository<WorkEvent, UUID> {

    /**
     * All of today's events for one employee, oldest first - the workday
     * timeline.
     *
     * <p>Everything the mappers read is fetched with the events. These results
     * are mapped to DTOs by callers that may be outside the loading
     * transaction, where a lazy association would fail rather than merely cost
     * an extra query. All of the joins are to-one, so there is no row
     * multiplication.
     */
    @Query("""
            select e from WorkEvent e
              join fetch e.employee
              left join fetch e.location
              left join fetch e.destinationLocation
              left join fetch e.photo
              left join fetch e.credential
            where e.employee.id = :employeeId
              and e.businessDate = :businessDate
            order by e.eventTime asc
            """)
    List<WorkEvent> findByEmployeeIdAndBusinessDateOrderByEventTimeAsc(@Param("employeeId") UUID employeeId,
                                                                       @Param("businessDate") LocalDate businessDate);

    /** As above, over a date range. */
    @Query("""
            select e from WorkEvent e
              join fetch e.employee
              left join fetch e.location
              left join fetch e.destinationLocation
              left join fetch e.photo
              left join fetch e.credential
            where e.employee.id = :employeeId
              and e.businessDate between :from and :to
            order by e.eventTime asc
            """)
    List<WorkEvent> findByEmployeeIdAndBusinessDateBetweenOrderByEventTimeAsc(@Param("employeeId") UUID employeeId,
                                                                              @Param("from") LocalDate from,
                                                                              @Param("to") LocalDate to);

    Page<WorkEvent> findByEmployeeIdOrderByEventTimeDesc(UUID employeeId, Pageable pageable);

    @Query("""
            select e from WorkEvent e
              left join fetch e.location
              left join fetch e.photo
              left join fetch e.destinationLocation
              join fetch e.employee
            where e.id = :id
            """)
    Optional<WorkEvent> findDetailedById(@Param("id") UUID id);

    /**
     * Map / monitoring search.
     *
     * <p>{@code employeeIds} carries the caller's authorised scope and is never
     * null for supervisors. The date range is always bound - see
     * {@link AuditLogRepository#search} for why.
     */
    @Query("""
            select e from WorkEvent e
              join fetch e.employee emp
              left join fetch e.location l
            where e.businessDate >= :from
              and e.businessDate <= :to
              and (:employeeId is null or emp.id = :employeeId)
              and (:branch is null or emp.branch = :branch)
              and (:category is null or emp.workCategory = :category)
              and (:locationId is null or l.id = :locationId)
              and (:eventType is null or e.eventType = :eventType)
              and (:scoped = false or emp.id in :employeeIds)
            order by e.eventTime desc
            """)
    List<WorkEvent> search(@Param("from") LocalDate from,
                           @Param("to") LocalDate to,
                           @Param("employeeId") UUID employeeId,
                           @Param("branch") Branch branch,
                           @Param("category") WorkCategory category,
                           @Param("locationId") UUID locationId,
                           @Param("eventType") EventType eventType,
                           @Param("scoped") boolean scoped,
                           @Param("employeeIds") Collection<UUID> employeeIds);

    /** The most recent event for each of the given employees ("latest recorded location"). */
    @Query("""
            select e from WorkEvent e
              join fetch e.employee emp
              left join fetch e.location
            where emp.id in :employeeIds
              and e.eventTime = (
                select max(e2.eventTime) from WorkEvent e2 where e2.employee.id = emp.id
              )
            """)
    List<WorkEvent> findLatestForEmployees(@Param("employeeIds") Collection<UUID> employeeIds);

    /**
     * The exceptions queue. Flags are fetched with the events: the review screen
     * shows every flag on every row, so loading them lazily one event at a time
     * would be a query per row for no benefit.
     */
    @Query("""
            select distinct e from WorkEvent e
              join fetch e.employee emp
              left join fetch e.location
              left join fetch e.flags
            where e.reviewStatus = :status
            order by e.eventTime desc
            """)
    List<WorkEvent> findByReviewStatus(@Param("status") ReviewStatus status);

    @Query("""
            select e from WorkEvent e
              join fetch e.employee emp
              left join fetch e.location
            order by e.eventTime desc
            """)
    List<WorkEvent> findRecent(Pageable pageable);

    @Query("""
            select count(distinct e.employee.id) from WorkEvent e
            where e.businessDate = :date and e.eventType = :type
            """)
    long countDistinctEmployeesWithEvent(@Param("date") LocalDate date, @Param("type") EventType type);

    @Query("""
            select e from WorkEvent e
              join fetch e.employee emp
              left join fetch e.location
            where e.businessDate = :date
            order by e.eventTime asc
            """)
    List<WorkEvent> findAllOnDate(@Param("date") LocalDate date);

    @Query("""
            select e from WorkEvent e
              join fetch e.employee emp
              left join fetch e.location
            where e.businessDate between :from and :to
              and (:scoped = false or emp.id in :employeeIds)
            order by emp.lastName asc, e.eventTime asc
            """)
    List<WorkEvent> findForReporting(@Param("from") LocalDate from,
                                     @Param("to") LocalDate to,
                                     @Param("scoped") boolean scoped,
                                     @Param("employeeIds") Collection<UUID> employeeIds);

    boolean existsByEmployeeIdAndBusinessDateAndEventType(UUID employeeId, LocalDate businessDate, EventType eventType);

    long countByReviewStatus(ReviewStatus status);

    long countByBusinessDate(LocalDate date);

    /** The last day this employee recorded anything, or null if they never have. */
    @Query("select max(e.businessDate) from WorkEvent e where e.employee.id = :employeeId")
    LocalDate findLatestBusinessDate(@Param("employeeId") UUID employeeId);

    @Query("delete from WorkEvent e where e.eventTime < :cutoff")
    @org.springframework.data.jpa.repository.Modifying
    int deleteOlderThan(@Param("cutoff") Instant cutoff);

    /**
     * Warehouse names this employee has used before, most recently first.
     *
     * <p>Feeds the suggestion chips on the arrival screen. Typing a name on a
     * phone is exactly the sort of friction the employee UI is meant to avoid,
     * so the second visit to a site should be a tap.
     */
    @Query("""
            select e.siteName from WorkEvent e
             where e.employee.id = :employeeId
               and e.siteName is not null
             group by e.siteName
             order by max(e.eventTime) desc
            """)
    List<String> findRecentSiteNames(@Param("employeeId") UUID employeeId, Pageable pageable);
}
