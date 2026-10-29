package com.uniace.trackr.repo;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniace.trackr.domain.WorkSchedule;
import com.uniace.trackr.support.DatabaseCleaner;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Wall-clock columns must survive the database round trip unchanged.
 *
 * <p>This exists because of a real bug: with {@code hibernate.jdbc.time_zone}
 * set to UTC, Hibernate applies a UTC calendar to plain {@code TIME} and
 * {@code DATE} columns as well as timestamps. A work schedule seeded by
 * migration as 08:00 then read back as 16:00 on a UTC+8 server - quietly
 * wrecking every lateness and overtime figure derived from it.
 *
 * <p>An API-only test would not have caught it: writing 08:00 and reading 08:00
 * back looks correct even when the value stored in between is wrong. So these
 * tests write with raw SQL and read with JPA, and the other way round, which is
 * the only way to see the shift.
 */
@SpringBootTest
@ActiveProfiles("test")
class TimeColumnMappingTest {

    @Autowired private WorkScheduleRepository scheduleRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DatabaseCleaner cleaner;

    @BeforeEach
    void setUp() {
        cleaner.clean();
    }

    @AfterEach
    void tearDown() {
        cleaner.clean();
    }

    @Test
    @DisplayName("a schedule inserted by SQL is read back with the same wall-clock times")
    void sqlInsertReadsBackUnshifted() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into work_schedules
                    (id, name, start_time, end_time, working_days,
                     break_start, break_end, break_paid, timezone, active, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, current_timestamp, current_timestamp)
                """,
                id, "Migration Seeded", LocalTime.of(8, 0), LocalTime.of(17, 0),
                "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY",
                LocalTime.of(12, 0), LocalTime.of(13, 0), false, "Asia/Manila", true);

        WorkSchedule loaded = scheduleRepository.findById(id).orElseThrow();

        assertThat(loaded.getStartTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(loaded.getEndTime()).isEqualTo(LocalTime.of(17, 0));
        assertThat(loaded.getBreakStart()).isEqualTo(LocalTime.of(12, 0));
        assertThat(loaded.getBreakEnd()).isEqualTo(LocalTime.of(13, 0));

        // The figure everything else is measured against.
        assertThat(loaded.scheduledWorkDuration()).isEqualTo(Duration.ofHours(8));
    }

    @Test
    @DisplayName("a schedule saved through JPA is stored with the same wall-clock times")
    void jpaSaveStoresUnshifted() {
        WorkSchedule schedule = new WorkSchedule();
        schedule.setName("Saved Through JPA");
        schedule.setStartTime(LocalTime.of(9, 30));
        schedule.setEndTime(LocalTime.of(18, 30));
        schedule.setWorkingDaySet(Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY));
        schedule.setBreakStart(LocalTime.of(12, 15));
        schedule.setBreakEnd(LocalTime.of(13, 15));
        schedule.setTimezone("Asia/Manila");

        WorkSchedule saved = scheduleRepository.saveAndFlush(schedule);

        // Read the raw column, not the entity: a shift applied on both write and
        // read is invisible to a round trip through JPA alone.
        String stored = jdbcTemplate.queryForObject(
                "select cast(start_time as varchar) from work_schedules where id = ?",
                String.class, saved.getId());

        assertThat(stored).startsWith("09:30");
    }
}
