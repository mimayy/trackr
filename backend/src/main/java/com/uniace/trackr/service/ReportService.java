package com.uniace.trackr.service;

import com.uniace.trackr.domain.Employee;
import com.uniace.trackr.domain.Holiday;
import com.uniace.trackr.domain.WorkEvent;
import com.uniace.trackr.domain.enums.*;
import com.uniace.trackr.dto.ReportDtos.*;
import com.uniace.trackr.repo.HolidayRepository;
import com.uniace.trackr.repo.WorkEventRepository;
import com.uniace.trackr.security.AuthPrincipal;
import com.uniace.trackr.service.attendance.AttendanceCalculator;
import com.uniace.trackr.service.attendance.AttendanceDay;
import com.uniace.trackr.web.error.ApiException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reports, all built from stored records rather than from anything cached or
 * pre-aggregated.
 *
 * <p>Scope applies to reports exactly as it does everywhere else: a supervisor's
 * report covers their own employees, an administrator's covers the company.
 */
@Service
public class ReportService {

    private static final int MAX_RANGE_DAYS = 400;

    private final WorkEventRepository workEventRepository;
    private final HolidayRepository holidayRepository;
    private final ScopeService scopeService;
    private final SettingsService settingsService;
    private final EventMapper mapper;

    public ReportService(WorkEventRepository workEventRepository,
                         HolidayRepository holidayRepository,
                         ScopeService scopeService,
                         SettingsService settingsService,
                         EventMapper mapper) {
        this.workEventRepository = workEventRepository;
        this.holidayRepository = holidayRepository;
        this.scopeService = scopeService;
        this.settingsService = settingsService;
        this.mapper = mapper;
    }

    // -------------------------------------------------------------------------
    // Attendance
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public AttendanceReport attendance(AuthPrincipal principal,
                                       LocalDate from,
                                       LocalDate to,
                                       Branch branch,
                                       WorkCategory category,
                                       UUID employeeId) {

        Range range = validate(from, to);
        List<Employee> employees = employeesFor(principal, branch, category, employeeId);
        Map<UUID, List<WorkEvent>> eventsByEmployeeDay = eventsByEmployeeAndDay(principal, range);
        List<Holiday> holidays = holidayRepository.findByDateBetweenOrderByDateAsc(range.from(), range.to());

        List<AttendanceReportRow> rows = new ArrayList<>();
        double totalHours = 0;
        long totalLate = 0;
        long totalUndertime = 0;
        long totalOvertime = 0;
        long present = 0;
        long absent = 0;
        long incomplete = 0;

        for (Employee employee : employees) {
            for (LocalDate date = range.from(); !date.isAfter(range.to()); date = date.plusDays(1)) {
                List<WorkEvent> events = eventsByEmployeeDay.getOrDefault(key(employee.getId(), date), List.of());

                AttendanceDay day = AttendanceCalculator.compute(
                        employee, employee.getWorkSchedule(), date, events, holidays, settingsService.attendanceRules());

                // Rest days and holidays with no activity add nothing to a report.
                if (day.timeIn() == null && (day.restDay() || day.holiday())) {
                    continue;
                }

                rows.add(new AttendanceReportRow(
                        employee.getId(),
                        employee.getEmployeeNo(),
                        employee.fullName(),
                        employee.getBranch(),
                        employee.getWorkCategory(),
                        date,
                        day.scheduleName(),
                        day.scheduledStart(),
                        day.scheduledEnd(),
                        day.timeIn(),
                        day.timeOut(),
                        day.hoursWorked(),
                        day.workedLabel(),
                        day.lateMinutes(),
                        day.undertimeMinutes(),
                        day.overtimeMinutes(),
                        day.statuses()));

                totalHours += day.hoursWorked();
                totalLate += day.lateMinutes();
                totalUndertime += day.undertimeMinutes();
                totalOvertime += day.overtimeMinutes();

                if (day.statuses().contains(AttendanceStatus.ABSENT)) {
                    absent++;
                } else if (day.statuses().contains(AttendanceStatus.INCOMPLETE)) {
                    incomplete++;
                    present++;
                } else if (day.timeIn() != null) {
                    present++;
                }
            }
        }

        rows.sort(Comparator.comparing(AttendanceReportRow::employeeName)
                .thenComparing(AttendanceReportRow::date));

        return new AttendanceReport(
                meta("attendance", range, rows.size()),
                rows,
                new AttendanceTotals(
                        Math.round(totalHours * 100.0) / 100.0,
                        totalLate, totalUndertime, totalOvertime,
                        present, absent, incomplete));
    }

    // -------------------------------------------------------------------------
    // Attendance lateness (kept separate from appointment lateness)
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public LateReport late(AuthPrincipal principal,
                           LocalDate from,
                           LocalDate to,
                           Branch branch,
                           WorkCategory category,
                           UUID employeeId) {

        AttendanceReport attendance = attendance(principal, from, to, branch, category, employeeId);

        List<LateReportRow> rows = attendance.rows().stream()
                .filter(row -> row.lateMinutes() > 0)
                .map(row -> new LateReportRow(
                        row.employeeId(),
                        row.employeeNo(),
                        row.employeeName(),
                        row.branch(),
                        row.date(),
                        row.scheduledStart(),
                        row.timeIn(),
                        row.timeIn() == null ? null
                                : LocalDateTime.ofInstant(row.timeIn(), ZoneId.of("Asia/Manila")).toLocalTime(),
                        row.lateMinutes()))
                .sorted(Comparator.comparingLong(LateReportRow::minutesLate).reversed())
                .toList();

        return new LateReport(meta("late", new Range(from, to), rows.size()), rows);
    }

    // -------------------------------------------------------------------------
    // Field activity
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public FieldActivityReport fieldActivity(AuthPrincipal principal,
                                             LocalDate from,
                                             LocalDate to,
                                             Branch branch,
                                             UUID employeeId) {

        Range range = validate(from, to);
        UUID supervisorFilter = scopeService.supervisorFilter(principal);
        if (employeeId != null) {
            scopeService.requireVisibleEmployee(principal, employeeId);
        }

        // Pair each field arrival with the departure that follows it. Without a
        // booking to report against, a "trip" is simply what the employee
        // recorded: where they said they were, and how long they stayed.
        List<WorkEvent> events = workEventRepository.findForReporting(
                range.from(), range.to(),
                supervisorFilter != null,
                scopeService.visibleEmployeeIds(principal));

        Map<UUID, List<WorkEvent>> byEmployee = new LinkedHashMap<>();
        for (WorkEvent event : events) {
            if (employeeId != null && !event.getEmployee().getId().equals(employeeId)) {
                continue;
            }
            if (branch != null && event.getEmployee().getBranch() != branch) {
                continue;
            }
            byEmployee.computeIfAbsent(event.getEmployee().getId(), key -> new ArrayList<>()).add(event);
        }

        List<FieldActivityRow> rows = new ArrayList<>();
        for (List<WorkEvent> timeline : byEmployee.values()) {
            WorkEvent arrival = null;
            for (WorkEvent event : timeline) {
                if (event.getEventType() == EventType.FIELD_ARRIVAL) {
                    if (arrival != null) {
                        rows.add(tripRow(arrival, null));
                    }
                    arrival = event;
                } else if (event.getEventType() == EventType.FIELD_DEPARTURE && arrival != null) {
                    rows.add(tripRow(arrival, event));
                    arrival = null;
                }
            }
            // Still on site when the range ended, or they forgot to record it.
            if (arrival != null) {
                rows.add(tripRow(arrival, null));
            }
        }

        return new FieldActivityReport(meta("field-activity", range, rows.size()), rows);
    }

    /** One visit: the arrival the employee recorded, and the departure if there was one. */
    private static FieldActivityRow tripRow(WorkEvent arrival, WorkEvent departure) {
        Long minutes = departure == null ? null
                : java.time.Duration.between(arrival.getEventTime(), departure.getEventTime()).toMinutes();

        return new FieldActivityRow(
                arrival.getEmployee().getId(),
                arrival.getEmployee().getEmployeeNo(),
                arrival.getEmployee().fullName(),
                arrival.getSiteName(),
                arrival.getBusinessDate(),
                arrival.getEventTime(),
                departure == null ? null : departure.getEventTime(),
                minutes,
                departure == null ? null : Labels.destinationLabel(departure.getDestination()));
    }


    // -------------------------------------------------------------------------
    // Office activity
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public OfficeActivityReport officeActivity(AuthPrincipal principal,
                                               LocalDate from,
                                               LocalDate to,
                                               UUID employeeId) {

        Range range = validate(from, to);
        if (employeeId != null) {
            scopeService.requireVisibleEmployee(principal, employeeId);
        }

        List<WorkEvent> events = scopedEvents(principal, range).stream()
                .filter(event -> employeeId == null || event.getEmployee().getId().equals(employeeId))
                .filter(event -> event.getEventType() == EventType.OFFICE_DEPARTURE
                        || event.getEventType() == EventType.OFFICE_ARRIVAL)
                .sorted(Comparator.comparing(WorkEvent::getEventTime))
                .toList();

        // Pair each departure with the next arrival by the same employee on the
        // same day; a departure with no matching arrival is still reported.
        Map<UUID, WorkEvent> openDepartures = new HashMap<>();
        List<OfficeActivityRow> rows = new ArrayList<>();

        for (WorkEvent event : events) {
            UUID key = key(event.getEmployee().getId(), event.getBusinessDate());

            if (event.getEventType() == EventType.OFFICE_DEPARTURE) {
                WorkEvent unmatched = openDepartures.put(key, event);
                if (unmatched != null) {
                    rows.add(row(unmatched, null));
                }
            } else {
                WorkEvent departure = openDepartures.remove(key);
                rows.add(row(departure, event));
            }
        }
        openDepartures.values().forEach(departure -> rows.add(row(departure, null)));

        rows.sort(Comparator.comparing(OfficeActivityRow::date)
                .thenComparing(OfficeActivityRow::employeeName));

        return new OfficeActivityReport(meta("office-activity", range, rows.size()), rows);
    }

    private static OfficeActivityRow row(WorkEvent departure, WorkEvent arrival) {
        WorkEvent any = departure != null ? departure : arrival;
        Employee employee = any.getEmployee();

        Long minutesAway = (departure != null && arrival != null)
                ? java.time.Duration.between(departure.getEventTime(), arrival.getEventTime()).toMinutes()
                : null;

        return new OfficeActivityRow(
                employee.getId(),
                employee.getEmployeeNo(),
                employee.fullName(),
                any.getBusinessDate(),
                departure == null ? null : departure.getEventTime(),
                arrival == null ? null : arrival.getEventTime(),
                minutesAway,
                departure == null ? null : Labels.destinationLabel(departure.getDestination()));
    }

    // -------------------------------------------------------------------------
    // Employee activity timeline
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public EmployeeActivityReport employeeActivity(AuthPrincipal principal,
                                                   UUID employeeId,
                                                   LocalDate from,
                                                   LocalDate to) {
        Range range = validate(from, to);
        Employee employee = scopeService.requireVisibleEmployee(principal, employeeId);

        List<WorkEvent> events = workEventRepository
                .findByEmployeeIdAndBusinessDateBetweenOrderByEventTimeAsc(employeeId, range.from(), range.to());

        return new EmployeeActivityReport(
                meta("employee-activity", range, events.size()),
                employee.getId(),
                employee.fullName(),
                employee.getEmployeeNo(),
                events.stream().map(mapper::toStaffView).toList());
    }

    // -------------------------------------------------------------------------
    // Shared helpers
    // -------------------------------------------------------------------------

    private List<Employee> employeesFor(AuthPrincipal principal, Branch branch, WorkCategory category, UUID employeeId) {
        if (employeeId != null) {
            return List.of(scopeService.requireVisibleEmployee(principal, employeeId));
        }
        return scopeService.visibleEmployees(principal).stream()
                .filter(employee -> branch == null || employee.getBranch() == branch)
                .filter(employee -> category == null || employee.getWorkCategory() == category)
                .toList();
    }

    private List<WorkEvent> scopedEvents(AuthPrincipal principal, Range range) {
        return workEventRepository.findForReporting(
                range.from(), range.to(),
                scopeService.isScoped(principal),
                scopeService.visibleEmployeeIds(principal));
    }

    private Map<UUID, List<WorkEvent>> eventsByEmployeeAndDay(AuthPrincipal principal, Range range) {
        Map<UUID, List<WorkEvent>> byKey = new HashMap<>();
        for (WorkEvent event : scopedEvents(principal, range)) {
            byKey.computeIfAbsent(key(event.getEmployee().getId(), event.getBusinessDate()),
                    k -> new ArrayList<>()).add(event);
        }
        return byKey;
    }

    /**
     * Employee-and-day composite key. A UUID is derived from the pair so the map
     * stays typed as {@code Map<UUID, ...>} without stringly-typed keys leaking
     * into the report code.
     */
    private static UUID key(UUID employeeId, LocalDate date) {
        return UUID.nameUUIDFromBytes((employeeId + "|" + date).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private Range validate(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw ApiException.badRequest("DATE_RANGE_REQUIRED", "Please choose a start and end date.");
        }
        if (to.isBefore(from)) {
            throw ApiException.badRequest("DATE_RANGE_INVALID", "The end date must be on or after the start date.");
        }
        if (from.plusDays(MAX_RANGE_DAYS).isBefore(to)) {
            throw ApiException.badRequest("DATE_RANGE_TOO_LARGE",
                    "Please choose a range of a year or less.");
        }
        return new Range(from, to);
    }

    private static ReportMeta meta(String type, Range range, int rowCount) {
        return new ReportMeta(type, range.from(), range.to(), rowCount, Instant.now());
    }

    private record Range(LocalDate from, LocalDate to) {
    }
}
