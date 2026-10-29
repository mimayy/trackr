package com.uniace.trackr.service.attendance;

import com.uniace.trackr.domain.enums.AttendanceStatus;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

/** One employee's attendance for one calendar day, derived from work events. */
public record AttendanceDay(
        UUID employeeId,
        String employeeNo,
        String employeeName,
        LocalDate date,
        String scheduleName,
        LocalTime scheduledStart,
        LocalTime scheduledEnd,
        Instant timeIn,
        Instant timeOut,
        LocalTime localTimeIn,
        LocalTime localTimeOut,
        Duration worked,
        Duration unpaidBreakDeducted,
        Duration scheduled,
        long lateMinutes,
        long undertimeMinutes,
        long overtimeMinutes,
        boolean restDay,
        boolean holiday,
        String holidayName,
        boolean complete,
        Set<AttendanceStatus> statuses
) {

    /** Hours worked, rounded to two decimals, for display and reports. */
    public double hoursWorked() {
        if (worked == null) {
            return 0d;
        }
        return Math.round(worked.toMinutes() / 60.0 * 100.0) / 100.0;
    }

    /** "7h 40m" style label used across the UI. */
    public String workedLabel() {
        if (worked == null) {
            return "--";
        }
        long hours = worked.toHours();
        long minutes = worked.toMinutesPart();
        return minutes == 0 ? hours + "h" : hours + "h " + minutes + "m";
    }
}
