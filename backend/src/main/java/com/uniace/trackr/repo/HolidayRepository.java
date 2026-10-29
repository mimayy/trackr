package com.uniace.trackr.repo;

import com.uniace.trackr.domain.Holiday;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HolidayRepository extends JpaRepository<Holiday, UUID> {

    List<Holiday> findByDateBetweenOrderByDateAsc(LocalDate from, LocalDate to);

    List<Holiday> findByDate(LocalDate date);

    List<Holiday> findAllByOrderByDateAsc();
}
