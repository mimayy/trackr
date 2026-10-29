package com.uniace.trackr.domain.enums;

/**
 * The six work events Trackr records. There are deliberately no extra
 * WORK_START / WORK_END / START_TRIP events: Time In marks the start of the
 * workday and Time Out marks the end of it.
 */
public enum EventType {
    TIME_IN,
    OFFICE_DEPARTURE,
    FIELD_ARRIVAL,
    FIELD_DEPARTURE,
    OFFICE_ARRIVAL,
    TIME_OUT;

    /** Events that require the employee to be physically at a known location. */
    public boolean requiresLocationMatch() {
        return this == FIELD_ARRIVAL || this == OFFICE_ARRIVAL || this == TIME_IN;
    }
}
