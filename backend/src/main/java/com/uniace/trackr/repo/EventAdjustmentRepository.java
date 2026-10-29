package com.uniace.trackr.repo;

import com.uniace.trackr.domain.EventAdjustment;
import com.uniace.trackr.domain.enums.AdjustmentStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventAdjustmentRepository extends JpaRepository<EventAdjustment, UUID> {

    List<EventAdjustment> findByWorkEventIdOrderByRequestedAtAsc(UUID workEventId);

    List<EventAdjustment> findByStatusOrderByRequestedAtDesc(AdjustmentStatus status);
}
