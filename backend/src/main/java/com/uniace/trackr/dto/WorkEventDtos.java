package com.uniace.trackr.dto;

import com.uniace.trackr.domain.enums.*;
import com.uniace.trackr.dto.WebAuthnDtos.DeviceAssertion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class WorkEventDtos {

    private WorkEventDtos() {
    }

    /**
     * One recorded work event as submitted by the employee's device.
     *
     * <p>{@code eventId} is generated on the device. Re-sending the same id
     * never creates a second event, which is what makes the offline queue safe
     * to retry.
     */
    public record RecordEventRequest(
            @NotNull(message = "Missing event reference") UUID eventId,
            @NotNull(message = "Please choose what you are recording") EventType eventType,
            @NotNull(message = "Missing the time of this activity") Instant eventTime,
            Instant deviceTimestamp,
            @DecimalMin(value = "-90.0") @DecimalMax(value = "90.0") Double latitude,
            @DecimalMin(value = "-180.0") @DecimalMax(value = "180.0") Double longitude,
            @DecimalMin(value = "0.0") Double gpsAccuracyM,
            // The warehouse or site the employee typed at Field Arrival.
            @Size(max = 120) String siteName,
            Destination destination,
            UUID destinationLocationId,
            UUID photoId,
            @Valid DeviceAssertion deviceAssertion,
            CaptureMode captureMode,
            Instant syncTimestamp,
            @Size(max = 120) String integrityHash,
            @Size(max = 40) String clientAppVersion
    ) {
        public CaptureMode captureModeOrOnline() {
            return captureMode == null ? CaptureMode.ONLINE : captureMode;
        }
    }

    /** A batch of queued offline events. */
    public record SyncRequest(
            @NotNull @Size(min = 1, max = 100, message = "Too many activities in one upload")
            List<@Valid RecordEventRequest> events
    ) {
    }

    /** What happened to one event in a sync batch. */
    public record SyncResultItem(
            UUID eventId,
            String status,
            String message,
            List<FlagView> flags
    ) {
        public static SyncResultItem accepted(UUID id) {
            return new SyncResultItem(id, "ACCEPTED", "Recorded.", List.of());
        }

        public static SyncResultItem duplicate(UUID id) {
            return new SyncResultItem(id, "DUPLICATE", "Already recorded.", List.of());
        }

        public static SyncResultItem flagged(UUID id, String message, List<FlagView> flags) {
            return new SyncResultItem(id, "FLAGGED", message, flags);
        }

        public static SyncResultItem failed(UUID id, String message) {
            return new SyncResultItem(id, "FAILED", message, List.of());
        }
    }

    public record SyncResponse(int accepted, int flagged, int duplicates, int failed, List<SyncResultItem> results) {
    }

    public record FlagView(FlagCategory category, String code, FlagSeverity severity, String label, String detail) {
    }

    public record LocationView(UUID id, String name, LocationType type, String address,
                               Double latitude, Double longitude, Integer geofenceRadiusM) {
    }

    /**
     * A work event as an employee sees it: verification shown as plain ticks,
     * never as coordinates, credential ids or status codes.
     */
    public record EmployeeEventView(
            UUID id,
            EventType eventType,
            String eventLabel,
            Instant eventTime,
            LocalDate businessDate,
            String locationName,
            Destination destination,
            String destinationName,
            boolean locationVerified,
            boolean fingerprintVerified,
            boolean hasPhoto,
            UUID photoId,
            SyncStatus syncStatus,
            CaptureMode captureMode,
            boolean needsReview,
            Double latitude,
            Double longitude,
            String siteName
    ) {
    }

    /** The same event with the operational detail supervisors and administrators need. */
    public record StaffEventView(
            UUID id,
            UUID employeeId,
            String employeeNo,
            String employeeName,
            Branch branch,
            WorkCategory workCategory,
            EventType eventType,
            String eventLabel,
            Instant eventTime,
            Instant deviceTimestamp,
            Instant syncTimestamp,
            Instant receivedAt,
            LocalDate businessDate,
            LocationView location,
            Double latitude,
            Double longitude,
            Double gpsAccuracyM,
            Double distanceToLocationM,
            Boolean withinGeofence,
            Destination destination,
            String destinationName,
            boolean biometricVerified,
            String deviceLabel,
            boolean hasPhoto,
            UUID photoId,
            CaptureMode captureMode,
            SyncStatus syncStatus,
            Disposition disposition,
            ReviewStatus reviewStatus,
            String reviewNotes,
            String siteName,
            List<FlagView> flags
    ) {
    }

    /** What the employee's home screen needs to show exactly one next action. */
    public record WorkflowStatusResponse(
            WorkflowState state,
            String statusLabel,
            String statusDetail,
            Instant workdayStartedAt,
            String currentLocationName,
            List<ActionOption> allowedActions,
            ActionOption nextAction,
            boolean requiresDestination,
            boolean fingerprintAvailable,
            boolean photoSupported,
            // Warehouse names this employee has used before, newest first, so the
            // app can offer them as taps instead of making someone retype one.
            List<String> recentSiteNames,
            List<EmployeeEventView> todaysEvents
    ) {
    }

    /** One button the employee may press, already worded for them. */
    public record ActionOption(
            EventType eventType,
            String label,
            String helper,
            boolean requiresLocation,
            boolean requiresSiteName
    ) {
    }


    /**
     * One day of attendance, flattened for the wire.
     *
     * <p>Durations are sent as plain numbers plus a ready-made label, so no
     * client has to parse an ISO-8601 duration or re-derive "7h 40m" itself.
     */
    public record AttendanceDayView(
            LocalDate date,
            String scheduleName,
            java.time.LocalTime scheduledStart,
            java.time.LocalTime scheduledEnd,
            Instant timeIn,
            Instant timeOut,
            double hoursWorked,
            String hoursWorkedLabel,
            long unpaidBreakMinutes,
            long lateMinutes,
            long undertimeMinutes,
            long overtimeMinutes,
            boolean restDay,
            boolean holiday,
            String holidayName,
            boolean complete,
            java.util.Set<AttendanceStatus> statuses
    ) {
        public static AttendanceDayView from(com.uniace.trackr.service.attendance.AttendanceDay day) {
            return new AttendanceDayView(
                    day.date(),
                    day.scheduleName(),
                    day.scheduledStart(),
                    day.scheduledEnd(),
                    day.timeIn(),
                    day.timeOut(),
                    day.hoursWorked(),
                    day.workedLabel(),
                    day.unpaidBreakDeducted() == null ? 0 : day.unpaidBreakDeducted().toMinutes(),
                    day.lateMinutes(),
                    day.undertimeMinutes(),
                    day.overtimeMinutes(),
                    day.restDay(),
                    day.holiday(),
                    day.holidayName(),
                    day.complete(),
                    day.statuses());
        }
    }

    /** "Latest Recorded Location" - deliberately never called a live location. */
    public record LatestLocationView(
            UUID employeeId,
            String employeeNo,
            String employeeName,
            Branch branch,
            WorkCategory workCategory,
            AccountStatus accountStatus,
            EventType lastEventType,
            String lastEventLabel,
            Instant lastEventTime,
            String locationName,
            Double latitude,
            Double longitude,
            WorkflowState workflowState,
            String statusLabel
    ) {
    }
}
