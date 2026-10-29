package com.uniace.trackr.domain;

import jakarta.persistence.*;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A configurable work schedule. Nothing about working hours or breaks is
 * hard-coded anywhere in the application: attendance is always calculated from
 * the schedule attached to the employee.
 */
@Entity
@Table(name = "work_schedules")
public class WorkSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 120)
    private String name;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    /** Comma separated {@link DayOfWeek} names, e.g. {@code MONDAY,TUESDAY}. */
    @Column(name = "working_days", nullable = false, length = 120)
    private String workingDays;

    @Column(name = "break_start")
    private LocalTime breakStart;

    @Column(name = "break_end")
    private LocalTime breakEnd;

    /** When false (the default) the break is unpaid and deducted from hours worked. */
    @Column(name = "break_paid", nullable = false)
    private boolean breakPaid;

    @Column(nullable = false, length = 60)
    private String timezone = "Asia/Manila";

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public Set<DayOfWeek> workingDaySet() {
        if (workingDays == null || workingDays.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(workingDays.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(DayOfWeek::valueOf)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public void setWorkingDaySet(Set<DayOfWeek> days) {
        this.workingDays = days.stream()
                .sorted()
                .map(Enum::name)
                .collect(Collectors.joining(","));
    }

    public boolean isWorkingDay(DayOfWeek day) {
        return workingDaySet().contains(day);
    }

    public ZoneId zone() {
        return ZoneId.of(timezone);
    }

    /** Length of the unpaid break, or zero when there is none (or it is paid). */
    public Duration unpaidBreak() {
        if (breakPaid || breakStart == null || breakEnd == null) {
            return Duration.ZERO;
        }
        return Duration.between(breakStart, breakEnd);
    }

    /** Scheduled paid hours for one working day. */
    public Duration scheduledWorkDuration() {
        Duration gross = Duration.between(startTime, endTime);
        if (gross.isNegative()) {
            // Overnight schedule.
            gross = gross.plusDays(1);
        }
        return gross.minus(unpaidBreak());
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    public String getWorkingDays() { return workingDays; }
    public void setWorkingDays(String workingDays) { this.workingDays = workingDays; }

    public LocalTime getBreakStart() { return breakStart; }
    public void setBreakStart(LocalTime breakStart) { this.breakStart = breakStart; }

    public LocalTime getBreakEnd() { return breakEnd; }
    public void setBreakEnd(LocalTime breakEnd) { this.breakEnd = breakEnd; }

    public boolean isBreakPaid() { return breakPaid; }
    public void setBreakPaid(boolean breakPaid) { this.breakPaid = breakPaid; }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
