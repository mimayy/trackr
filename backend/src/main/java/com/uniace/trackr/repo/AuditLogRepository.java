package com.uniace.trackr.repo;

import com.uniace.trackr.domain.AuditLog;
import com.uniace.trackr.domain.enums.AuditResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    /**
     * Audit search.
     *
     * <p>{@code from} and {@code to} are always bound - never null. PostgreSQL
     * cannot infer a type for a bare parameter in {@code ? is null}, so an
     * optional timestamp written that way fails at runtime; a wide-open range
     * expresses "no filter" without the type ambiguity.
     */
    @Query("""
            select a from AuditLog a
            where a.occurredAt >= :from
              and a.occurredAt <= :to
              and (:actorId is null or a.actor.id = :actorId)
              and (:action is null or a.action = :action)
              and (:result is null or a.result = :result)
            order by a.occurredAt desc
            """)
    Page<AuditLog> search(@Param("from") Instant from,
                          @Param("to") Instant to,
                          @Param("actorId") UUID actorId,
                          @Param("action") String action,
                          @Param("result") AuditResult result,
                          Pageable pageable);

    @Query("select distinct a.action from AuditLog a order by a.action asc")
    List<String> findDistinctActions();
}
