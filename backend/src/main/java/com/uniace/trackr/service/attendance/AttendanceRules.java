package com.uniace.trackr.service.attendance;

/**
 * The configurable thresholds attendance is judged against. Nothing here has a
 * hard-coded value at a call site - the rules come from system settings, which
 * the administrator can change.
 */
public record AttendanceRules(
        int lateGraceMinutes,
        int overtimeThresholdMinutes,
        int maxWorkdayHours
) {

    public static AttendanceRules defaults() {
        return new AttendanceRules(0, 30, 16);
    }
}
