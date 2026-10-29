package com.uniace.trackr.dto;

import com.uniace.trackr.domain.enums.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class AdminDtos {

    private AdminDtos() {
    }

    // -------------------------------------------------------------------------
    // Employees
    // -------------------------------------------------------------------------

    /**
     * What the administrator fills in to create an employee.
     *
     * <p>Notably absent: anything about devices. The employee registers their
     * own phone during activation, so the administrator never picks one.
     */
    public record CreateEmployeeRequest(
            @NotBlank(message = "Employee ID is required")
            @Pattern(regexp = "^[A-Za-z0-9-]{3,40}$", message = "Use letters, numbers and dashes only")
            String employeeNo,

            @NotBlank(message = "First name is required") @Size(max = 80) String firstName,
            @NotBlank(message = "Last name is required") @Size(max = 80) String lastName,
            @NotBlank(message = "Email is required") @Email(message = "Please check the email address") String email,
            @Size(max = 40) String contactNumber,

            @NotNull(message = "Please choose a branch") Branch branch,
            @NotNull(message = "Please choose a work category") WorkCategory workCategory,

            UUID supervisorId,
            UUID workScheduleId,
            UUID homeOfficeId,
            LocalDate hiredOn
    ) {
    }

    public record UpdateEmployeeRequest(
            @Size(max = 80) String firstName,
            @Size(max = 80) String lastName,
            @Email(message = "Please check the email address") String email,
            @Size(max = 40) String contactNumber,
            Branch branch,
            WorkCategory workCategory,
            UUID supervisorId,
            UUID workScheduleId,
            UUID homeOfficeId,
            LocalDate hiredOn
    ) {
    }

    /** One row of the administrator's employee list. */
    public record EmployeeRow(
            UUID id,
            String employeeNo,
            String fullName,
            String email,
            String contactNumber,
            Branch branch,
            WorkCategory workCategory,
            String supervisorName,
            UUID supervisorId,
            String scheduleName,
            UUID scheduleId,
            AccountStatus accountStatus,
            CredentialStatus deviceStatus,
            String deviceLabel,
            String latestLocationName,
            Instant latestEventTime,
            EventType latestEventType,
            String latestEventLabel
    ) {
    }

    public record EmployeeDetail(
            EmployeeRow summary,
            String homeOfficeName,
            LocalDate hiredOn,
            Instant createdAt,
            Instant lastLoginAt,
            DeviceSecurityView device,
            boolean invitationPending,
            Instant invitationExpiresAt
    ) {
    }

    /** The administrator's per-employee security panel. */
    public record DeviceSecurityView(
            UUID credentialId,
            String deviceLabel,
            CredentialStatus status,
            String algorithm,
            Instant registeredAt,
            Instant lastUsedAt,
            Instant revokedAt,
            String revokedReason
    ) {
    }

    public record RevokeDeviceRequest(@Size(max = 300) String reason) {
    }

    public record DisableEmployeeRequest(@Size(max = 300) String reason) {
    }

    // -------------------------------------------------------------------------
    // Supervisors
    // -------------------------------------------------------------------------

    public record CreateSupervisorRequest(
            @NotBlank(message = "First name is required") @Size(max = 80) String firstName,
            @NotBlank(message = "Last name is required") @Size(max = 80) String lastName,
            @NotBlank(message = "Email is required") @Email(message = "Please check the email address") String email,
            @Size(max = 40) String contactNumber,
            @NotNull(message = "Please choose a branch") Branch branch,
            @Size(max = 300) String scopeNote
    ) {
    }

    public record UpdateSupervisorRequest(
            @Size(max = 80) String firstName,
            @Size(max = 80) String lastName,
            @Email(message = "Please check the email address") String email,
            @Size(max = 40) String contactNumber,
            Branch branch,
            @Size(max = 300) String scopeNote
    ) {
    }

    public record SupervisorRow(
            UUID id,
            String fullName,
            String email,
            String contactNumber,
            Branch branch,
            String scopeNote,
            AccountStatus accountStatus,
            long employeeCount,
            boolean active
    ) {
    }

    public record AssignEmployeesRequest(
            @NotNull List<UUID> employeeIds
    ) {
    }

    // -------------------------------------------------------------------------
    // Locations
    // -------------------------------------------------------------------------

    public record LocationRequest(
            @NotBlank(message = "Name is required") @Size(max = 160) String name,
            @NotNull(message = "Please choose a location type") LocationType type,
            Branch branch,
            @Size(max = 400) String address,
            @NotNull(message = "Latitude is required") @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
            @NotNull(message = "Longitude is required") @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
            @NotNull(message = "Geofence radius is required") @Min(10) @Max(5000) Integer geofenceRadiusM,
            Boolean active
    ) {
    }

    public record LocationRow(
            UUID id,
            String name,
            LocationType type,
            Branch branch,
            String address,
            double latitude,
            double longitude,
            int geofenceRadiusM,
            boolean active
    ) {
    }

    // -------------------------------------------------------------------------
    // Work schedules and holidays
    // -------------------------------------------------------------------------

    public record ScheduleRequest(
            @NotBlank(message = "Schedule name is required") @Size(max = 120) String name,
            @NotNull(message = "Start time is required") LocalTime startTime,
            @NotNull(message = "End time is required") LocalTime endTime,
            @NotEmpty(message = "Choose at least one working day") Set<String> workingDays,
            LocalTime breakStart,
            LocalTime breakEnd,
            Boolean breakPaid,
            @Size(max = 60) String timezone,
            Boolean active
    ) {
    }

    public record ScheduleRow(
            UUID id,
            String name,
            LocalTime startTime,
            LocalTime endTime,
            Set<String> workingDays,
            LocalTime breakStart,
            LocalTime breakEnd,
            boolean breakPaid,
            String timezone,
            boolean active,
            double scheduledHours,
            long employeeCount
    ) {
    }

    public record HolidayRequest(
            @NotNull(message = "Date is required") LocalDate date,
            @NotBlank(message = "Name is required") @Size(max = 160) String name,
            Branch branch,
            Boolean paid
    ) {
    }

    public record HolidayRow(UUID id, LocalDate date, String name, Branch branch, boolean paid) {
    }

    // -------------------------------------------------------------------------
    // Dashboards, exceptions, audit and settings
    // -------------------------------------------------------------------------

    public record DashboardResponse(
            LocalDate date,
            long totalEmployees,
            long present,
            long late,
            long absent,
            long inField,
            long atOffice,
            long finished,
            long pendingActivation,
            long requiresReview,
            List<WorkEventDtos.StaffEventView> recentActivity,
            List<WorkEventDtos.LatestLocationView> latestLocations
    ) {
    }

    public record ExceptionRow(
            UUID eventId,
            UUID employeeId,
            String employeeName,
            String employeeNo,
            EventType eventType,
            String eventLabel,
            Instant eventTime,
            LocalDate businessDate,
            String locationName,
            CaptureMode captureMode,
            ReviewStatus reviewStatus,
            List<WorkEventDtos.FlagView> flags,
            FlagSeverity highestSeverity
    ) {
    }

    public record ReviewRequest(
            @NotNull ReviewStatus reviewStatus,
            @Size(max = 1000) String notes
    ) {
    }

    public record AdjustmentRequest(
            @NotNull(message = "Choose what to correct") AdjustmentField field,
            @NotBlank(message = "New value is required") @Size(max = 500) String adjustedValue,
            @NotBlank(message = "A reason is required") @Size(max = 1000) String reason
    ) {
    }

    public record AdjustmentDecision(
            @NotNull AdjustmentStatus status,
            @Size(max = 1000) String note
    ) {
    }

    public record AdjustmentRow(
            UUID id,
            UUID eventId,
            AdjustmentField field,
            String originalValue,
            String adjustedValue,
            String reason,
            AdjustmentStatus status,
            String requestedByName,
            Instant requestedAt,
            String decidedByName,
            Instant decidedAt,
            String decisionNote
    ) {
    }

    public record AuditRow(
            UUID id,
            Instant occurredAt,
            String actorLabel,
            String actorRole,
            String action,
            String targetType,
            String targetId,
            String targetLabel,
            AuditResult result,
            String detail,
            String ipAddress
    ) {
    }

    public record SettingRow(String key, String value, String description, Instant updatedAt, String updatedByName) {
    }

    public record SettingUpdateRequest(@NotBlank @Size(max = 400) String value) {
    }
}
