package com.uniace.trackr.repo;

import com.uniace.trackr.domain.WorkSchedule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkScheduleRepository extends JpaRepository<WorkSchedule, UUID> {

    Optional<WorkSchedule> findByNameIgnoreCase(String name);

    List<WorkSchedule> findByActiveTrueOrderByNameAsc();

    List<WorkSchedule> findAllByOrderByNameAsc();
}
