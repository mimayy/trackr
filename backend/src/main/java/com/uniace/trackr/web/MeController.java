package com.uniace.trackr.web;

import com.uniace.trackr.domain.Employee;
import com.uniace.trackr.domain.PhotoEvidence;
import com.uniace.trackr.domain.UserAccount;
import com.uniace.trackr.domain.enums.AuditResult;
import com.uniace.trackr.dto.WebAuthnDtos.*;
import com.uniace.trackr.dto.WorkEventDtos.*;
import com.uniace.trackr.security.AuthPrincipal;
import com.uniace.trackr.service.*;
import com.uniace.trackr.service.attendance.AttendanceCalculator;
import com.uniace.trackr.service.attendance.AttendanceDay;
import com.uniace.trackr.web.error.ApiException;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Everything an employee can reach.
 *
 * <p>No endpoint here takes an employee id: the employee is always the person
 * holding the token. That is what makes it impossible for one employee to read
 * another's attendance by editing a URL.
 */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final ScopeService scopeService;
    private final WorkflowService workflowService;
    private final WorkEventService workEventService;
    private final SyncService syncService;
    private final PhotoService photoService;
    private final WebAuthnService webAuthnService;
    private final ScheduleService scheduleService;
    private final SettingsService settingsService;
    private final AuditService auditService;
    private final EventMapper mapper;

    public MeController(ScopeService scopeService,
                        WorkflowService workflowService,
                        WorkEventService workEventService,
                        SyncService syncService,
                        PhotoService photoService,
                        WebAuthnService webAuthnService,
                        ScheduleService scheduleService,
                        SettingsService settingsService,
                        AuditService auditService,
                        EventMapper mapper) {
        this.scopeService = scopeService;
        this.workflowService = workflowService;
        this.workEventService = workEventService;
        this.syncService = syncService;
        this.photoService = photoService;
        this.webAuthnService = webAuthnService;
        this.scheduleService = scheduleService;
        this.settingsService = settingsService;
        this.auditService = auditService;
        this.mapper = mapper;
    }

    // -------------------------------------------------------------------------
    // Home screen
    // -------------------------------------------------------------------------

    @GetMapping("/status")
    public WorkflowStatusResponse status(@AuthenticationPrincipal AuthPrincipal principal) {
        return workflowService.statusFor(scopeService.requireEmployee(principal));
    }

    // -------------------------------------------------------------------------
    // Recording work events
    // -------------------------------------------------------------------------

    @PostMapping("/work-events")
    public ResponseEntity<Map<String, Object>> record(@AuthenticationPrincipal AuthPrincipal principal,
                                                      @Valid @RequestBody RecordEventRequest request) {
        UserAccount user = scopeService.requireUser(principal);
        Employee employee = scopeService.requireEmployee(principal);

        var result = workEventService.recordOnline(user, employee, request);

        if (!result.duplicate()) {
            auditService.record(principal, "RECORD_WORK_EVENT", "WorkEvent",
                    result.event().getId().toString(), employee.fullName(),
                    result.flagged() ? AuditResult.FLAGGED : AuditResult.SUCCESS,
                    result.event().getEventType().name());
        }

        return ResponseEntity.ok(Map.of(
                "event", mapper.toEmployeeView(result.event()),
                "duplicate", result.duplicate(),
                "status", workflowService.statusFor(employee)));
    }

    /** Uploads the offline queue. Safe to call repeatedly with the same events. */
    @PostMapping("/sync/work-events")
    public SyncResponse sync(@AuthenticationPrincipal AuthPrincipal principal,
                             @Valid @RequestBody SyncRequest request) {
        UserAccount user = scopeService.requireUser(principal);
        Employee employee = scopeService.requireEmployee(principal);
        return syncService.sync(principal, user, employee, request.events());
    }

    /** Lets the device confirm which queued events the server already holds. */
    @PostMapping("/sync/status")
    public Map<String, Object> syncStatus(@AuthenticationPrincipal AuthPrincipal principal,
                                          @RequestBody List<UUID> eventIds) {
        Employee employee = scopeService.requireEmployee(principal);
        List<UUID> known = new ArrayList<>();

        for (UUID id : eventIds) {
            try {
                var event = workEventService.require(id);
                if (event.getEmployee().getId().equals(employee.getId())) {
                    known.add(id);
                }
            } catch (ApiException ignored) {
                // Not stored yet - the device should keep it queued.
            }
        }
        return Map.of("stored", known);
    }

    // -------------------------------------------------------------------------
    // History
    // -------------------------------------------------------------------------

    @GetMapping("/work-events")
    public List<EmployeeEventView> events(@AuthenticationPrincipal AuthPrincipal principal,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Employee employee = scopeService.requireEmployee(principal);
        LocalDate today = LocalDate.now(workEventService.scheduleZone(employee));
        LocalDate start = from == null ? today.minusDays(30) : from;
        LocalDate end = to == null ? today : to;

        return workEventService.eventsBetween(employee, start, end).stream()
                .map(mapper::toEmployeeView)
                .toList();
    }

    @GetMapping("/work-events/{id}")
    public EmployeeEventView event(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        Employee employee = scopeService.requireEmployee(principal);
        var event = workEventService.require(id);

        if (!event.getEmployee().getId().equals(employee.getId())) {
            throw ApiException.forbidden("You do not have access to this activity.");
        }
        return mapper.toEmployeeView(event);
    }

    /** The employee's own attendance history, one row per day, newest first. */
    @GetMapping("/attendance")
    public List<AttendanceDayView> attendance(@AuthenticationPrincipal AuthPrincipal principal,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Employee employee = scopeService.requireEmployee(principal);
        LocalDate today = LocalDate.now(workEventService.scheduleZone(employee));
        LocalDate start = from == null ? today.minusDays(13) : from;
        LocalDate end = to == null ? today : to;

        var events = workEventService.eventsBetween(employee, start, end);
        var holidays = scheduleService.holidaysBetween(start, end);
        var rules = settingsService.attendanceRules();

        List<AttendanceDayView> days = new ArrayList<>();
        for (LocalDate date = end; !date.isBefore(start); date = date.minusDays(1)) {
            final LocalDate current = date;
            var forDay = events.stream().filter(e -> e.getBusinessDate().equals(current)).toList();
            AttendanceDay day = AttendanceCalculator.compute(
                    employee, employee.getWorkSchedule(), current, forDay, holidays, rules);
            days.add(AttendanceDayView.from(day));
        }
        return days;
    }

    // -------------------------------------------------------------------------
    // Photo evidence
    // -------------------------------------------------------------------------

    @PostMapping(value = "/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadPhoto(@AuthenticationPrincipal AuthPrincipal principal,
                                           @RequestPart("file") MultipartFile file,
                                           @RequestParam(required = false) Instant capturedAt) {
        Employee employee = scopeService.requireEmployee(principal);
        PhotoEvidence photo = photoService.upload(employee, file, capturedAt);
        return Map.of("photoId", photo.getId());
    }

    @GetMapping("/photos/{id}")
    public ResponseEntity<byte[]> photo(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        var stored = photoService.download(principal, id);
        return ResponseEntity.ok()
                .header("Content-Type", stored.contentType())
                .header("Cache-Control", "private, max-age=3600")
                .body(stored.content());
    }

    // -------------------------------------------------------------------------
    // Fingerprint (WebAuthn)
    // -------------------------------------------------------------------------

    @PostMapping("/webauthn/register/start")
    public RegistrationOptions startRegistration(@AuthenticationPrincipal AuthPrincipal principal) {
        return webAuthnService.startRegistration(
                scopeService.requireUser(principal), scopeService.requireEmployee(principal));
    }

    @PostMapping("/webauthn/register/finish")
    public Map<String, Object> finishRegistration(@AuthenticationPrincipal AuthPrincipal principal,
                                                  @Valid @RequestBody RegistrationRequest request) {
        UserAccount user = scopeService.requireUser(principal);
        Employee employee = scopeService.requireEmployee(principal);

        var credential = webAuthnService.finishRegistration(user, employee, request);
        auditService.success(principal, "REGISTER_DEVICE_CREDENTIAL", "Employee",
                employee.getId(), employee.getEmployeeNo(),
                "Registered device: " + credential.getDeviceLabel());

        return Map.of(
                "registered", true,
                "deviceLabel", credential.getDeviceLabel(),
                "registeredAt", credential.getCreatedAt());
    }

    /**
     * Issues a challenge the phone signs with the employee's fingerprint. The
     * resulting assertion is attached to the work event being recorded.
     */
    @PostMapping("/webauthn/verify/start")
    public AuthenticationOptions startVerification(@AuthenticationPrincipal AuthPrincipal principal) {
        return webAuthnService.startAuthentication(
                scopeService.requireUser(principal), scopeService.requireEmployee(principal));
    }

    @GetMapping("/device")
    public Map<String, Object> device(@AuthenticationPrincipal AuthPrincipal principal) {
        Employee employee = scopeService.requireEmployee(principal);
        return webAuthnService.activeCredential(employee)
                .<Map<String, Object>>map(credential -> Map.of(
                        "registered", true,
                        "deviceLabel", credential.getDeviceLabel(),
                        "registeredAt", credential.getCreatedAt(),
                        "lastUsedAt", credential.getLastUsedAt() == null ? "" : credential.getLastUsedAt()))
                .orElse(Map.of("registered", false));
    }
}
