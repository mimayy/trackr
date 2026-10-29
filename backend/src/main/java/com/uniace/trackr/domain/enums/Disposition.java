package com.uniace.trackr.domain.enums;

/**
 * Whether the server accepted the event cleanly or stored it with concerns.
 * Events that cannot be safely accepted at all are rejected before they reach
 * this stage and never get a disposition.
 */
public enum Disposition {
    ACCEPTED,
    FLAGGED
}
