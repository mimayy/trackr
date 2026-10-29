package com.uniace.trackr.domain.enums;

/**
 * A profile attribute, not a live location. A FIELD_BASED employee regularly
 * works at the office on days with no site visit, and that does not
 * change their category.
 */
public enum WorkCategory {
    OFFICE_BASED,
    FIELD_BASED
}
