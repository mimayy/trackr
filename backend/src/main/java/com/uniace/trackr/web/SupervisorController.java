package com.uniace.trackr.web;

import com.uniace.trackr.domain.enums.*;
import com.uniace.trackr.dto.AdminDtos.*;
import com.uniace.trackr.dto.ReportDtos.*;
import com.uniace.trackr.dto.WorkEventDtos.LatestLocationView;
import com.uniace.trackr.dto.WorkEventDtos.StaffEventView;
import com.uniace.trackr.security.AuthPrincipal;
import com.uniace.trackr.service.*;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Supervisor console.
 *
 * <p>Administrators may call these too - they simply see everyone. Every method
 * resolves scope through {@link ScopeService} rather than trusting a request
 * parameter, so a supervisor asking about an employee outside their team gets
 * the same refusal as if that employee did not exist.
 */
@RestController
@RequestMapping("/api/supervisor")
public class SupervisorController {

    private final ScopeService scopeService;
    private final EmployeeService employeeService;
    private final MonitoringService monitoringService;
    private final DashboardService dashboardService;
    private final ReportService reportService;
    private final ReviewService reviewService;
    private final WorkEventService workEventService;
    private final PhotoService photoService;
    private final LocationService locationService;
    private final EventMapper mapper;

    public SupervisorController(ScopeService scopeService,
                                EmployeeService employeeService,
                                MonitoringService monitoringService,
                                DashboardService dashboardService,
                                ReportService reportService,
                                ReviewService reviewService,
                                WorkEventService workEventService,
                                PhotoService photoService,
                                LocationService locationService,
                                EventMapper mapper) {
        this.scopeService = scopeService;
        this.employeeService = employeeService;
        this.monitoringService = monitoringService;
        this.dashboardService = dashboardService;
        this.reportService = reportService;
        this.reviewService = reviewService;
        this.workEventService = workEventService;
        this.photoService = photoService;
        this.locationService = locationService;
        this.mapper = mapper;
    }

    // -------------------------------------------------------------------------
    // Dashboard and people
    // -------------------------------------------------------------------------

    @GetMapping("/dashboard")
    public DashboardResponse dashboard(@AuthenticationPrincipal AuthPrincipal principal,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return dashboardService.forDate(principal, date == null ? LocalDate.now() : date);
    }

    @GetMapping("/employees")
    public List<EmployeeRow> employees(@AuthenticationPrincipal AuthPrincipal principal,
                                       @RequestParam(required = false) Branch branch,
                                       @RequestParam(required = false) WorkCategory workCategory) {
        var employees = scopeService.visibleEmployees(principal).stream()
                .filter(employee -> branch == null || employee.getBranch() == branch)
                .filter(employee -> workCategory == null || employee.getWorkCategory() == workCategory)
                .toList();
        return employeeService.decorate(employees);
    }

    @GetMapping("/employees/{id}")
    public EmployeeDetail employee(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        scopeService.requireVisibleEmployee(principal, id);
        return employeeService.detail(id);
    }

    @GetMapping("/employees/{id}/work-events")
    public List<StaffEventView> employeeEvents(@AuthenticationPrincipal AuthPrincipal principal,
                                               @PathVariable UUID id,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return monitoringService.timelineFor(principal, id, date == null ? LocalDate.now() : date);
    }

    // -------------------------------------------------------------------------
    // Monitoring / map
    // -------------------------------------------------------------------------

    /** Recorded work-event positions. Never a live feed - see MonitoringService. */
    @GetMapping("/monitoring/work-events")
    public List<StaffEventView> monitoring(@AuthenticationPrincipal AuthPrincipal principal,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                           @RequestParam(required = false) UUID employeeId,
                                           @RequestParam(required = false) Branch branch,
                                           @RequestParam(required = false) WorkCategory workCategory,
                                           @RequestParam(required = false) UUID locationId,
                                           @RequestParam(required = false) EventType eventType) {
        LocalDate today = LocalDate.now();
        return monitoringService.searchEvents(principal,
                from == null ? today : from,
                to == null ? today : to,
                employeeId, branch, workCategory, locationId, eventType);
    }

    @GetMapping("/monitoring/latest-locations")
    public List<LatestLocationView> latestLocations(@AuthenticationPrincipal AuthPrincipal principal,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return monitoringService.latestLocations(principal, date == null ? LocalDate.now() : date);
    }

    @GetMapping("/work-events/{id}")
    public StaffEventView workEvent(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        var event = workEventService.require(id);
        scopeService.requireVisibleEmployee(principal, event.getEmployee().getId());
        return mapper.toStaffView(event);
    }

    // -------------------------------------------------------------------------
    // Exceptions
    // -------------------------------------------------------------------------

    @GetMapping("/exceptions")
    public List<ExceptionRow> exceptions(@AuthenticationPrincipal AuthPrincipal principal,
                                         @RequestParam(required = false) FlagCategory category) {
        return reviewService.queue(principal, category);
    }

    @PostMapping("/work-events/{id}/review")
    public ExceptionRow review(@AuthenticationPrincipal AuthPrincipal principal,
                               @PathVariable UUID id,
                               @Valid @RequestBody ReviewRequest request) {
        return reviewService.review(principal, id, request);
    }

    // -------------------------------------------------------------------------
    // Reports
    // -------------------------------------------------------------------------

    @GetMapping("/reports/attendance")
    public AttendanceReport attendanceReport(@AuthenticationPrincipal AuthPrincipal principal,
                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                             @RequestParam(required = false) Branch branch,
                                             @RequestParam(required = false) WorkCategory workCategory,
                                             @RequestParam(required = false) UUID employeeId) {
        return reportService.attendance(principal, from, to, branch, workCategory, employeeId);
    }

    @GetMapping("/reports/late")
    public LateReport lateReport(@AuthenticationPrincipal AuthPrincipal principal,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                 @RequestParam(required = false) Branch branch,
                                 @RequestParam(required = false) WorkCategory workCategory,
                                 @RequestParam(required = false) UUID employeeId) {
        return reportService.late(principal, from, to, branch, workCategory, employeeId);
    }

    @GetMapping("/reports/field-activity")
    public FieldActivityReport fieldActivityReport(@AuthenticationPrincipal AuthPrincipal principal,
                                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                   @RequestParam(required = false) Branch branch,
                                                   @RequestParam(required = false) UUID employeeId) {
        return reportService.fieldActivity(principal, from, to, branch, employeeId);
    }

    @GetMapping("/reports/office-activity")
    public OfficeActivityReport officeActivityReport(@AuthenticationPrincipal AuthPrincipal principal,
                                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                     @RequestParam(required = false) UUID employeeId) {
        return reportService.officeActivity(principal, from, to, employeeId);
    }

    @GetMapping("/reports/employee-activity")
    public EmployeeActivityReport employeeActivityReport(@AuthenticationPrincipal AuthPrincipal principal,
                                                         @RequestParam UUID employeeId,
                                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.employeeActivity(principal, employeeId, from, to);
    }

    /**
     * Photo evidence for an event in scope. The authorisation check lives in
     * {@link PhotoService}, which refuses photos belonging to employees outside
     * the caller's team.
     */
    @GetMapping("/photos/{id}")
    public ResponseEntity<byte[]> photo(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        var stored = photoService.download(principal, id);
        return ResponseEntity.ok()
                .header("Content-Type", stored.contentType())
                .header("Cache-Control", "private, max-age=3600")
                .body(stored.content());
    }

    /** The offices and warehouses used by the map filters. */
    @GetMapping("/locations")
    public List<LocationRow> locations() {
        return locationService.listActive().stream()
                .map(location -> new LocationRow(
                        location.getId(),
                        location.getName(),
                        location.getType(),
                        location.getBranch(),
                        location.getAddress(),
                        location.getLatitude(),
                        location.getLongitude(),
                        location.getGeofenceRadiusM(),
                        location.isActive()))
                .toList();
    }
}
