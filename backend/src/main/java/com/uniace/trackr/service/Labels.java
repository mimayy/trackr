package com.uniace.trackr.service;

import com.uniace.trackr.domain.enums.Destination;
import com.uniace.trackr.domain.enums.EventType;
import com.uniace.trackr.domain.enums.WorkflowState;

/**
 * Plain-language wording for every enum the employee can see.
 *
 * <p>Kept in one place so the phone, the supervisor console and the reports all
 * say the same thing, and so nothing leaks an enum constant into the UI.
 */
public final class Labels {

    private Labels() {
    }

    public static String eventLabel(EventType type) {
        return switch (type) {
            case TIME_IN -> "Time In";
            case OFFICE_DEPARTURE -> "Office Departure";
            case FIELD_ARRIVAL -> "Field Arrival";
            case FIELD_DEPARTURE -> "Field Departure";
            case OFFICE_ARRIVAL -> "Office Arrival";
            case TIME_OUT -> "Time Out";
        };
    }

    /** The words that go on the employee's big button. */
    public static String actionLabel(EventType type) {
        return switch (type) {
            case TIME_IN -> "TIME IN";
            case OFFICE_DEPARTURE -> "LEAVE THE OFFICE";
            case FIELD_ARRIVAL -> "I'M HERE";
            case FIELD_DEPARTURE -> "I'M LEAVING";
            case OFFICE_ARRIVAL -> "I'M BACK AT THE OFFICE";
            case TIME_OUT -> "TIME OUT";
        };
    }

    /** One short sentence under the button saying what will happen. */
    public static String actionHelper(EventType type) {
        return switch (type) {
            case TIME_IN -> "Start your workday.";
            case OFFICE_DEPARTURE -> "Record that you are leaving the office.";
            case FIELD_ARRIVAL -> "Confirm you have arrived, and say which warehouse.";
            case FIELD_DEPARTURE -> "Record that you have finished here.";
            case OFFICE_ARRIVAL -> "Confirm you are back at the office.";
            case TIME_OUT -> "End your workday.";
        };
    }

    public static String stateLabel(WorkflowState state) {
        return switch (state) {
            case NOT_STARTED -> "NOT STARTED";
            case AT_OFFICE -> "WORKING";
            case EN_ROUTE_TO_FIELD -> "ON THE WAY";
            case AT_FIELD -> "ON SITE";
            case EN_ROUTE_TO_OFFICE -> "RETURNING TO OFFICE";
            case EN_ROUTE_HOME -> "HEADING HOME";
            case DAY_ENDED -> "FINISHED FOR TODAY";
        };
    }

    public static String stateDetail(WorkflowState state) {
        return switch (state) {
            case NOT_STARTED -> "Ready to start your workday?";
            case AT_OFFICE -> "You are timed in.";
            case EN_ROUTE_TO_FIELD -> "Record your arrival when you get there.";
            case AT_FIELD -> "Record your departure when you are finished.";
            case EN_ROUTE_TO_OFFICE -> "Confirm your arrival when you reach the office.";
            case EN_ROUTE_HOME -> "One thing left: Time Out.";
            case DAY_ENDED -> "Your workday is complete. See you tomorrow.";
        };
    }

    public static String destinationLabel(Destination destination) {
        if (destination == null) {
            return null;
        }
        return switch (destination) {
            case OFFICE -> "Office";
            case HOME -> "Home";
            case ANOTHER_FIELD_LOCATION -> "Another field location";
        };
    }

    /** Human wording for an exception code, used in the review screens. */
    public static String flagLabel(String code) {
        return switch (code) {
            case "GPS_UNAVAILABLE" -> "Location was not available";
            case "GPS_ACCURACY_LOW" -> "GPS accuracy was low";
            case "GPS_ACCURACY_TOO_LOW" -> "GPS accuracy was too low to verify";
            case "OUTSIDE_GEOFENCE" -> "Recorded outside the assigned area";
            case "LOCATION_UNKNOWN" -> "Not at a known Uni-Ace location";
            case "NO_FINGERPRINT" -> "Recorded without a fingerprint check";
            case "FINGERPRINT_FAILED" -> "Fingerprint check did not verify";
            case "CREDENTIAL_REVOKED" -> "Device registration was revoked";
            case "SIGN_COUNT_ANOMALY" -> "Device counter did not advance as expected";
            case "INVALID_SEQUENCE" -> "Activity recorded out of order";
            case "DUPLICATE_TIME_IN" -> "A second Time In for the same day";
            case "TIME_OUT_BEFORE_TIME_IN" -> "Time Out recorded before Time In";
            case "IMPOSSIBLE_DURATION" -> "Workday longer than allowed";
            case "CLOCK_SKEW" -> "Device clock did not match the server clock";
            case "LATE_SYNC" -> "Uploaded well after it was recorded";
            case "MISSING_SITE_NAME" -> "Arrival recorded without a site name";
            case "MISSING_DESTINATION" -> "No destination was chosen";
            case "INTEGRITY_MISMATCH" -> "The saved activity did not match its checksum";
            default -> code.replace('_', ' ').toLowerCase();
        };
    }
}
