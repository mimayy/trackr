package com.uniace.trackr.util;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Stand-ins for "no date filter".
 *
 * <p>Optional date filters are expressed as a wide-open range rather than a
 * null parameter, because PostgreSQL cannot infer a type for a bare parameter
 * in {@code ? is null} and the query fails at runtime. Binding a real bound
 * keeps the same meaning with none of the ambiguity.
 */
public final class DateRanges {

    /** Comfortably before Uni-Ace had any records. */
    public static final LocalDate EARLIEST_DATE = LocalDate.of(1970, 1, 1);

    /** Comfortably after anything this system will be asked about. */
    public static final LocalDate LATEST_DATE = LocalDate.of(2999, 12, 31);

    public static final Instant EARLIEST_INSTANT = Instant.EPOCH;
    public static final Instant LATEST_INSTANT = Instant.parse("2999-12-31T23:59:59Z");

    private DateRanges() {
    }

    public static LocalDate fromOrEarliest(LocalDate from) {
        return from == null ? EARLIEST_DATE : from;
    }

    public static LocalDate toOrLatest(LocalDate to) {
        return to == null ? LATEST_DATE : to;
    }

    public static Instant fromOrEarliest(Instant from) {
        return from == null ? EARLIEST_INSTANT : from;
    }

    public static Instant toOrLatest(Instant to) {
        return to == null ? LATEST_INSTANT : to;
    }
}
