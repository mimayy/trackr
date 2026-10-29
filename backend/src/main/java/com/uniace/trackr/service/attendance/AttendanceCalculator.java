package com.uniace.trackr.service.attendance;

import com.uniace.trackr.domain.Employee;
import com.uniace.trackr.domain.Holiday;
import com.uniace.trackr.domain.WorkEvent;
import com.uniace.trackr.domain.WorkSchedule;
import com.uniace.trackr.domain.enums.AttendanceStatus;
import com.uniace.trackr.domain.enums.EventType;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * Works out hours, lateness, undertime and overtime for one employee-day.
 *
 * <p>Deliberately a plain class with no Spring dependencies: it is the piece of
 * the system most worth testing exhaustively, and every input is explicit.
 *
 * <p>The unpaid break is never assumed to be an hour, and is never subtracted
 * blindly. Only the part of the break the employee actually spanned is
 * deducted, so someone who times out at 11:00 does not lose a lunch they never
 * took.
 */
public final class AttendanceCalculator {

    private AttendanceCalculator() {
    }

    public static AttendanceDay compute(Employee employee,
                                        WorkSchedule schedule,
                                        LocalDate date,
                                        Collection<WorkEvent> eventsForDay,
                                        Collection<Holiday> holidays,
                                        AttendanceRules rules) {

        ZoneId zone = schedule != null ? schedule.zone() : ZoneId.of("Asia/Manila");

        Instant timeIn = firstOf(eventsForDay, EventType.TIME_IN);
        Instant timeOut = lastOf(eventsForDay, EventType.TIME_OUT);

        Holiday holiday = holidays.stream()
                .filter(h -> h.getDate().equals(date))
                .filter(h -> h.appliesTo(employee.getBranch()))
                .findFirst()
                .orElse(null);

        boolean restDay = schedule != null && !schedule.isWorkingDay(date.getDayOfWeek());

        LocalTime localIn = timeIn == null ? null : LocalDateTime.ofInstant(timeIn, zone).toLocalTime();
        LocalTime localOut = timeOut == null ? null : LocalDateTime.ofInstant(timeOut, zone).toLocalTime();

        Set<AttendanceStatus> statuses = EnumSet.noneOf(AttendanceStatus.class);
        if (restDay) {
            statuses.add(AttendanceStatus.REST_DAY);
        }
        if (holiday != null) {
            statuses.add(AttendanceStatus.HOLIDAY);
        }

        // Nobody clocked in: absent, unless the day was never a working day.
        if (timeIn == null) {
            if (!restDay && holiday == null) {
                statuses.add(AttendanceStatus.ABSENT);
            }
            return build(employee, schedule, date, holiday, restDay,
                    null, null, null, null,
                    null, Duration.ZERO, scheduledDuration(schedule),
                    0, 0, 0, false, statuses);
        }

        // Clocked in but never out - the day cannot be totalled yet.
        if (timeOut == null) {
            statuses.add(AttendanceStatus.INCOMPLETE);
            long lateMinutes = lateMinutes(schedule, restDay, localIn, rules);
            if (lateMinutes > 0) {
                statuses.add(AttendanceStatus.LATE);
            } else if (!restDay && holiday == null) {
                statuses.add(AttendanceStatus.ON_TIME);
            }
            return build(employee, schedule, date, holiday, restDay,
                    timeIn, null, localIn, null,
                    null, Duration.ZERO, scheduledDuration(schedule),
                    lateMinutes, 0, 0, false, statuses);
        }

        Duration elapsed = Duration.between(timeIn, timeOut);
        if (elapsed.isNegative()) {
            // Time Out before Time In. The events are kept and flagged
            // elsewhere; here the day simply has no usable total.
            statuses.add(AttendanceStatus.INCOMPLETE);
            return build(employee, schedule, date, holiday, restDay,
                    timeIn, timeOut, localIn, localOut,
                    null, Duration.ZERO, scheduledDuration(schedule),
                    0, 0, 0, false, statuses);
        }

        Duration breakDeduction = unpaidBreakOverlap(schedule, date, zone, timeIn, timeOut);
        Duration worked = elapsed.minus(breakDeduction);
        if (worked.isNegative()) {
            worked = Duration.ZERO;
        }

        long lateMinutes = lateMinutes(schedule, restDay, localIn, rules);
        Duration scheduled = scheduledDuration(schedule);

        long undertimeMinutes = 0;
        long overtimeMinutes = 0;
        if (schedule != null && !restDay) {
            long difference = worked.toMinutes() - scheduled.toMinutes();
            if (difference < 0) {
                undertimeMinutes = -difference;
            } else if (difference >= rules.overtimeThresholdMinutes()) {
                overtimeMinutes = difference;
            }
        }

        if (lateMinutes > 0) {
            statuses.add(AttendanceStatus.LATE);
        } else if (!restDay && holiday == null) {
            statuses.add(AttendanceStatus.ON_TIME);
        }
        if (undertimeMinutes > 0) {
            statuses.add(AttendanceStatus.UNDERTIME);
        }
        if (overtimeMinutes > 0) {
            statuses.add(AttendanceStatus.OVERTIME);
        }

        return build(employee, schedule, date, holiday, restDay,
                timeIn, timeOut, localIn, localOut,
                worked, breakDeduction, scheduled,
                lateMinutes, undertimeMinutes, overtimeMinutes, true, statuses);
    }

    /**
     * Attendance lateness only - how late the employee started their workday.
     * Field appointment lateness is a separate measure and is never mixed in
     * here (requirement 28).
     */
    public static long lateMinutes(WorkSchedule schedule, boolean restDay, LocalTime localTimeIn, AttendanceRules rules) {
        if (schedule == null || restDay || localTimeIn == null) {
            return 0;
        }
        LocalTime threshold = schedule.getStartTime().plusMinutes(rules.lateGraceMinutes());
        if (!localTimeIn.isAfter(threshold)) {
            return 0;
        }
        return Duration.between(threshold, localTimeIn).toMinutes();
    }

    /** Field appointment lateness: arrival time against the appointment time. */
    public static long appointmentLateMinutes(LocalTime appointmentTime, LocalTime arrivalTime) {
        if (appointmentTime == null || arrivalTime == null || !arrivalTime.isAfter(appointmentTime)) {
            return 0;
        }
        return Duration.between(appointmentTime, arrivalTime).toMinutes();
    }

    /**
     * How much of the configured unpaid break falls inside the worked window.
     * A paid break, or a schedule without one, deducts nothing.
     */
    static Duration unpaidBreakOverlap(WorkSchedule schedule,
                                       LocalDate date,
                                       ZoneId zone,
                                       Instant timeIn,
                                       Instant timeOut) {
        if (schedule == null || schedule.isBreakPaid()
                || schedule.getBreakStart() == null || schedule.getBreakEnd() == null) {
            return Duration.ZERO;
        }

        Instant breakStart = date.atTime(schedule.getBreakStart()).atZone(zone).toInstant();
        Instant breakEnd = date.atTime(schedule.getBreakEnd()).atZone(zone).toInstant();

        Instant overlapStart = timeIn.isAfter(breakStart) ? timeIn : breakStart;
        Instant overlapEnd = timeOut.isBefore(breakEnd) ? timeOut : breakEnd;

        Duration overlap = Duration.between(overlapStart, overlapEnd);
        return overlap.isNegative() ? Duration.ZERO : overlap;
    }

    private static Duration scheduledDuration(WorkSchedule schedule) {
        return schedule == null ? Duration.ZERO : schedule.scheduledWorkDuration();
    }

    private static Instant firstOf(Collection<WorkEvent> events, EventType type) {
        return events.stream()
                .filter(e -> e.getEventType() == type)
                .map(WorkEvent::getEventTime)
                .min(Instant::compareTo)
                .orElse(null);
    }

    private static Instant lastOf(Collection<WorkEvent> events, EventType type) {
        return events.stream()
                .filter(e -> e.getEventType() == type)
                .map(WorkEvent::getEventTime)
                .max(Instant::compareTo)
                .orElse(null);
    }

    private static AttendanceDay build(Employee employee,
                                       WorkSchedule schedule,
                                       LocalDate date,
                                       Holiday holiday,
                                       boolean restDay,
                                       Instant timeIn,
                                       Instant timeOut,
                                       LocalTime localIn,
                                       LocalTime localOut,
                                       Duration worked,
                                       Duration breakDeducted,
                                       Duration scheduled,
                                       long lateMinutes,
                                       long undertimeMinutes,
                                       long overtimeMinutes,
                                       boolean complete,
                                       Set<AttendanceStatus> statuses) {
        return new AttendanceDay(
                employee.getId(),
                employee.getEmployeeNo(),
                employee.fullName(),
                date,
                schedule == null ? null : schedule.getName(),
                schedule == null ? null : schedule.getStartTime(),
                schedule == null ? null : schedule.getEndTime(),
                timeIn,
                timeOut,
                localIn,
                localOut,
                worked,
                breakDeducted,
                scheduled,
                lateMinutes,
                undertimeMinutes,
                overtimeMinutes,
                restDay,
                holiday != null,
                holiday == null ? null : holiday.getName(),
                complete,
                Set.copyOf(statuses));
    }
}
