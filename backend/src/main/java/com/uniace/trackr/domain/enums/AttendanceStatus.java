package com.uniace.trackr.domain.enums;

/**
 * Per-day attendance outcome. A single day can carry several of these at once
 * (for example LATE together with OVERTIME), so they are used as a set.
 */
public enum AttendanceStatus {
    ON_TIME,
    LATE,
    UNDERTIME,
    OVERTIME,
    ABSENT,
    INCOMPLETE,
    REST_DAY,
    HOLIDAY
}
