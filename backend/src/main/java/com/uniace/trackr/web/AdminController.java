package com.uniace.trackr.web;

import com.uniace.trackr.domain.Employee;
import com.uniace.trackr.domain.Supervisor;
import com.uniace.trackr.domain.WorkLocation;
import com.uniace.trackr.domain.enums.*;
import com.uniace.trackr.dto.AdminDtos.*;
import com.uniace.trackr.dto.AuthDtos.InvitationResponse;
import com.uniace.trackr.repo.AuditLogRepository;
import com.uniace.trackr.repo.WorkLocationRepository;
import com.uniace.trackr.security.AuthPrincipal;
import com.uniace.trackr.util.DateRanges;
import com.uniace.trackr.service.*;
import com.uniace.trackr.web.error.ApiException;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Administrator console: the only role that can create accounts, configure the
 * system and read the audit trail.
 *
 * <p>The whole path is gated to ADMIN in {@link com.uniace.trackr.config.SecurityConfig},
 * so nothing here needs to re-check the role - only the individual business
 * rules.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final EmployeeService employeeService;
    private final SupervisorService supervisorService;
    private final ScheduleService scheduleService;
    private final LocationService locationService;
    private final WorkLocationRepository locationRepository;
    private final ActivationService activationService;
    private final SettingsService settingsService;
    private final ReviewService reviewService;
    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;
    private final ScopeService scopeService;
    private final PhotoService photoService;

    public AdminController(EmployeeService employeeService,
                           SupervisorService supervisorService,
                           ScheduleService scheduleService,
                           LocationService locationService,
                           WorkLocationRepository locationRepository,
                           ActivationService activationService,
                           SettingsService settingsService,
                           ReviewService reviewService,
                           AuditLogRepository auditLogRepository,
                           AuditService auditService,
                           ScopeService scopeService,
                           PhotoService photoService) {
        this.employeeService = employeeService;
        this.supervisorService = supervisorService;
        this.scheduleService = scheduleService;
        this.locationService = locationService;
        this.locationRepository = locationRepository;
        this.activationService = activationService;
        this.settingsService = settingsService;
        this.reviewService = reviewService;
        this.auditLogRepository = auditLogRepository;
        this.auditService = auditService;
        this.scopeService = scopeService;
        this.photoService = photoService;
    }

    // -------------------------------------------------------------------------
    // Employees
    // -------------------------------------------------------------------------

    @GetMapping("/employees")
    public List<EmployeeRow> employees(@RequestParam(required = false) Branch branch,
                                       @RequestParam(required = false) WorkCategory workCategory,
                                       @RequestParam(required = false) AccountStatus status,
                                       @RequestParam(required = false) UUID supervisorId) {
        return employeeService.list(branch, workCategory, status, supervisorId);
    }

    @GetMapping("/employees/{id}")
    public EmployeeDetail employee(@PathVariable UUID id) {
        return employeeService.detail(id);
    }

    @PostMapping("/employees")
    public EmployeeDetail createEmployee(@AuthenticationPrincipal AuthPrincipal principal,
                                         @Valid @RequestBody CreateEmployeeRequest request) {
        Employee created = employeeService.create(principal, request);
        return employeeService.detail(created.getId());
    }

    @PatchMapping("/employees/{id}")
    public EmployeeDetail updateEmployee(@AuthenticationPrincipal AuthPrincipal principal,
                                         @PathVariable UUID id,
                                         @Valid @RequestBody UpdateEmployeeRequest request) {
        employeeService.update(principal, id, request);
        return employeeService.detail(id);
    }

    @PostMapping("/employees/{id}/disable")
    public EmployeeDetail disableEmployee(@AuthenticationPrincipal AuthPrincipal principal,
                                          @PathVariable UUID id,
                                          @RequestBody(required = false) DisableEmployeeRequest request) {
        employeeService.setEnabled(principal, id, false, request == null ? null : request.reason());
        return employeeService.detail(id);
    }

    @PostMapping("/employees/{id}/enable")
    public EmployeeDetail enableEmployee(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        employeeService.setEnabled(principal, id, true, null);
        return employeeService.detail(id);
    }

    /**
     * Creates a fresh activation link. The raw link comes back exactly once - it
     * is stored only as a hash, so it cannot be looked up again later.
     */
    @PostMapping("/employees/{id}/invitation")
    public InvitationResponse invite(@AuthenticationPrincipal AuthPrincipal principal,
                                     @PathVariable UUID id,
                                     @RequestParam(defaultValue = "false") boolean sendEmail) {
        Employee employee = employeeService.require(id);
        var invitation = activationService.createInvitation(
                employee.getUser(), scopeService.requireUser(principal), sendEmail);

        auditService.success(principal, "CREATE_ACTIVATION_INVITATION", "Employee",
                employee.getId(), employee.getEmployeeNo(),
                "Invitation expires " + invitation.expiresAt() + (invitation.emailSent() ? ", emailed" : ""));

        return invitation;
    }

    /** Revokes the registered phone. The account itself stays active. */
    @PostMapping("/employees/{id}/device/revoke")
    public EmployeeDetail revokeDevice(@AuthenticationPrincipal AuthPrincipal principal,
                                       @PathVariable UUID id,
                                       @RequestBody(required = false) RevokeDeviceRequest request) {
        employeeService.revokeDevice(principal, id, request == null ? null : request.reason());
        return employeeService.detail(id);
    }

    /**
     * "Reset device credential": revokes the current phone and issues a fresh
     * activation link so the employee can register a new one.
     */
    @PostMapping("/employees/{id}/device/reset")
    public Map<String, Object> resetDevice(@AuthenticationPrincipal AuthPrincipal principal,
                                           @PathVariable UUID id,
                                           @RequestBody(required = false) RevokeDeviceRequest request) {
        Employee employee = employeeService.require(id);
        try {
            employeeService.revokeDevice(principal, id, request == null ? null : request.reason());
        } catch (ApiException ex) {
            if (!"NO_ACTIVE_DEVICE".equals(ex.getCode())) {
                throw ex;
            }
            // Nothing registered yet - resetting is still a valid thing to ask for.
        }

        var invitation = activationService.createInvitation(
                employee.getUser(), scopeService.requireUser(principal), false);

        auditService.success(principal, "RESET_DEVICE_CREDENTIAL", "Employee",
                employee.getId(), employee.getEmployeeNo(),
                "Device reset; new activation link issued");

        return Map.of(
                "employee", employeeService.detail(id),
                "invitation", invitation);
    }

    // -------------------------------------------------------------------------
    // Supervisors
    // -------------------------------------------------------------------------

    @GetMapping("/supervisors")
    public List<SupervisorRow> supervisors() {
        return supervisorService.list();
    }

    @PostMapping("/supervisors")
    public SupervisorRow createSupervisor(@AuthenticationPrincipal AuthPrincipal principal,
                                          @Valid @RequestBody CreateSupervisorRequest request) {
        return supervisorService.toRow(supervisorService.create(principal, request));
    }

    @PatchMapping("/supervisors/{id}")
    public SupervisorRow updateSupervisor(@AuthenticationPrincipal AuthPrincipal principal,
                                          @PathVariable UUID id,
                                          @Valid @RequestBody UpdateSupervisorRequest request) {
        return supervisorService.toRow(supervisorService.update(principal, id, request));
    }

    @PostMapping("/supervisors/{id}/disable")
    public SupervisorRow disableSupervisor(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        return supervisorService.toRow(supervisorService.setEnabled(principal, id, false));
    }

    @PostMapping("/supervisors/{id}/enable")
    public SupervisorRow enableSupervisor(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        return supervisorService.toRow(supervisorService.setEnabled(principal, id, true));
    }

    @PostMapping("/supervisors/{id}/employees")
    public Map<String, Object> assignEmployees(@AuthenticationPrincipal AuthPrincipal principal,
                                               @PathVariable UUID id,
                                               @Valid @RequestBody AssignEmployeesRequest request) {
        int count = supervisorService.assignEmployees(principal, id, request.employeeIds());
        return Map.of("assigned", count);
    }

    @PostMapping("/supervisors/{id}/invitation")
    public InvitationResponse inviteSupervisor(@AuthenticationPrincipal AuthPrincipal principal,
                                               @PathVariable UUID id,
                                               @RequestParam(defaultValue = "false") boolean sendEmail) {
        Supervisor supervisor = supervisorService.require(id);
        var invitation = activationService.createInvitation(
                supervisor.getUser(), scopeService.requireUser(principal), sendEmail);

        auditService.success(principal, "CREATE_ACTIVATION_INVITATION", "Supervisor",
                supervisor.getId(), supervisor.fullName(), "Invitation expires " + invitation.expiresAt());
        return invitation;
    }

    // -------------------------------------------------------------------------
    // Locations
    // -------------------------------------------------------------------------

    @GetMapping("/locations")
    public List<LocationRow> locations() {
        return locationService.listAll().stream().map(AdminController::toRow).toList();
    }

    @PostMapping("/locations")
    public LocationRow createLocation(@AuthenticationPrincipal AuthPrincipal principal,
                                      @Valid @RequestBody LocationRequest request) {
        locationRepository.findByNameIgnoreCase(request.name().trim()).ifPresent(existing -> {
            throw ApiException.conflict("LOCATION_NAME_TAKEN", "A location with that name already exists.");
        });

        WorkLocation location = new WorkLocation();
        apply(location, request);
        WorkLocation saved = locationService.save(location);

        auditService.success(principal, "CREATE_LOCATION", "Location", saved.getId(), saved.getName(),
                saved.getType() + " with a " + saved.getGeofenceRadiusM() + " m geofence");
        return toRow(saved);
    }

    @PatchMapping("/locations/{id}")
    public LocationRow updateLocation(@AuthenticationPrincipal AuthPrincipal principal,
                                      @PathVariable UUID id,
                                      @Valid @RequestBody LocationRequest request) {
        WorkLocation location = locationService.require(id);
        String before = location.getName() + " r=" + location.getGeofenceRadiusM() + "m";

        apply(location, request);
        WorkLocation saved = locationService.save(location);

        auditService.success(principal, "UPDATE_LOCATION", "Location", saved.getId(), saved.getName(),
                "Was: " + before + " | Now: " + saved.getName() + " r=" + saved.getGeofenceRadiusM() + "m");
        return toRow(saved);
    }

    // -------------------------------------------------------------------------
    // Work schedules and non-working dates
    // -------------------------------------------------------------------------

    @GetMapping("/schedules")
    public List<ScheduleRow> schedules() {
        return scheduleService.list();
    }

    @PostMapping("/schedules")
    public ScheduleRow createSchedule(@AuthenticationPrincipal AuthPrincipal principal,
                                      @Valid @RequestBody ScheduleRequest request) {
        return scheduleService.toRow(scheduleService.create(principal, request));
    }

    @PatchMapping("/schedules/{id}")
    public ScheduleRow updateSchedule(@AuthenticationPrincipal AuthPrincipal principal,
                                      @PathVariable UUID id,
                                      @Valid @RequestBody ScheduleRequest request) {
        return scheduleService.toRow(scheduleService.update(principal, id, request));
    }

    @GetMapping("/holidays")
    public List<HolidayRow> holidays() {
        return scheduleService.listHolidays();
    }

    @PostMapping("/holidays")
    public HolidayRow createHoliday(@AuthenticationPrincipal AuthPrincipal principal,
                                    @Valid @RequestBody HolidayRequest request) {
        var holiday = scheduleService.createHoliday(principal, request);
        return new HolidayRow(holiday.getId(), holiday.getDate(), holiday.getName(),
                holiday.getBranch(), holiday.isPaid());
    }

    @DeleteMapping("/holidays/{id}")
    public Map<String, String> deleteHoliday(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        scheduleService.deleteHoliday(principal, id);
        return Map.of("message", "Non-working date removed.");
    }

    // -------------------------------------------------------------------------
    // Corrections
    // -------------------------------------------------------------------------

    @GetMapping("/adjustments")
    public List<AdjustmentRow> pendingAdjustments() {
        return reviewService.pendingAdjustments();
    }

    @GetMapping("/work-events/{id}/adjustments")
    public List<AdjustmentRow> adjustments(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        return reviewService.adjustmentsFor(principal, id);
    }

    @PostMapping("/work-events/{id}/adjustments")
    public AdjustmentRow requestAdjustment(@AuthenticationPrincipal AuthPrincipal principal,
                                           @PathVariable UUID id,
                                           @Valid @RequestBody AdjustmentRequest request) {
        return reviewService.requestAdjustment(principal, id, request);
    }

    @PostMapping("/adjustments/{id}/decision")
    public AdjustmentRow decideAdjustment(@AuthenticationPrincipal AuthPrincipal principal,
                                          @PathVariable UUID id,
                                          @Valid @RequestBody AdjustmentDecision decision) {
        return reviewService.decide(principal, id, decision.status(), decision.note());
    }

    // -------------------------------------------------------------------------
    // Audit log
    // -------------------------------------------------------------------------

    @GetMapping("/audit-logs")
    public Page<AuditRow> auditLogs(@RequestParam(required = false) Instant from,
                                    @RequestParam(required = false) Instant to,
                                    @RequestParam(required = false) UUID actorId,
                                    @RequestParam(required = false) String action,
                                    @RequestParam(required = false) AuditResult result,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "50") int size) {

        return auditLogRepository
                .search(DateRanges.fromOrEarliest(from), DateRanges.toOrLatest(to),
                        actorId, action, result, PageRequest.of(page, Math.min(size, 200)))
                .map(log -> new AuditRow(
                        log.getId(), log.getOccurredAt(), log.getActorLabel(), log.getActorRole(),
                        log.getAction(), log.getTargetType(), log.getTargetId(), log.getTargetLabel(),
                        log.getResult(), log.getDetail(), log.getIpAddress()));
    }

    @GetMapping("/audit-logs/actions")
    public List<String> auditActions() {
        return auditLogRepository.findDistinctActions();
    }

    // -------------------------------------------------------------------------
    // System settings
    // -------------------------------------------------------------------------

    @GetMapping("/settings")
    public List<SettingRow> settings() {
        return settingsService.all().stream()
                .map(setting -> new SettingRow(
                        setting.getKey(),
                        setting.getValue(),
                        setting.getDescription(),
                        setting.getUpdatedAt(),
                        setting.getUpdatedBy() == null ? null : setting.getUpdatedBy().getDisplayName()))
                .toList();
    }

    @PatchMapping("/settings/{key}")
    public SettingRow updateSetting(@AuthenticationPrincipal AuthPrincipal principal,
                                    @PathVariable String key,
                                    @Valid @RequestBody SettingUpdateRequest request) {
        var setting = settingsService.update(key, request.value(), scopeService.requireUser(principal));
        auditService.record(principal, "UPDATE_SETTING", "SystemSetting", key, key,
                AuditResult.SUCCESS, "Set to " + request.value());

        return new SettingRow(setting.getKey(), setting.getValue(), setting.getDescription(),
                setting.getUpdatedAt(), principal.displayName());
    }

    @GetMapping("/system-info")
    public Map<String, Object> systemInfo() {
        return Map.of(
                "photoStorage", photoService.storageDescription(),
                "serverTime", Instant.now().toString(),
                "today", LocalDate.now().toString());
    }

    @GetMapping("/photos/{id}")
    public org.springframework.http.ResponseEntity<byte[]> photo(@AuthenticationPrincipal AuthPrincipal principal,
                                                                 @PathVariable UUID id) {
        var stored = photoService.download(principal, id);
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Type", stored.contentType())
                .header("Cache-Control", "private, max-age=3600")
                .body(stored.content());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void apply(WorkLocation location, LocationRequest request) {
        location.setName(request.name().trim());
        location.setType(request.type());
        location.setBranch(request.branch());
        location.setAddress(request.address() == null || request.address().isBlank()
                ? null : request.address().trim());
        location.setLatitude(request.latitude());
        location.setLongitude(request.longitude());
        location.setGeofenceRadiusM(request.geofenceRadiusM());
        location.setActive(request.active() == null || request.active());
    }

    private static LocationRow toRow(WorkLocation location) {
        return new LocationRow(
                location.getId(),
                location.getName(),
                location.getType(),
                location.getBranch(),
                location.getAddress(),
                location.getLatitude(),
                location.getLongitude(),
                location.getGeofenceRadiusM(),
                location.isActive());
    }
}
