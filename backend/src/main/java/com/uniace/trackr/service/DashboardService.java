package com.uniace.trackr.service;

import com.uniace.trackr.domain.Employee;
import com.uniace.trackr.domain.Holiday;
import com.uniace.trackr.domain.WorkEvent;
import com.uniace.trackr.domain.enums.*;
import com.uniace.trackr.dto.AdminDtos.DashboardResponse;
import com.uniace.trackr.dto.WorkEventDtos.StaffEventView;
import com.uniace.trackr.repo.HolidayRepository;
import com.uniace.trackr.repo.WorkEventRepository;
import com.uniace.trackr.security.AuthPrincipal;
import com.uniace.trackr.service.attendance.AttendanceCalculator;
import com.uniace.trackr.service.attendance.AttendanceDay;
import com.uniace.trackr.service.workflow.WorkflowEngine;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dashboard figures.
 *
 * <p>Every number here is counted from the records in the database for the day
 * being viewed. Nothing is a placeholder, so an empty database honestly shows
 * zeros rather than an invented headline.
 */
@Service
public class DashboardService {

    private final WorkEventRepository workEventRepository;
    private final HolidayRepository holidayRepository;
    private final ScopeService scopeService;
    private final MonitoringService monitoringService;
    private final SettingsService settingsService;
    private final EventMapper mapper;

    public DashboardService(WorkEventRepository workEventRepository,
                            HolidayRepository holidayRepository,
                            ScopeService scopeService,
                            MonitoringService monitoringService,
                            SettingsService settingsService,
                            EventMapper mapper) {
        this.workEventRepository = workEventRepository;
        this.holidayRepository = holidayRepository;
        this.scopeService = scopeService;
        this.monitoringService = monitoringService;
        this.settingsService = settingsService;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public DashboardResponse forDate(AuthPrincipal principal, LocalDate date) {
        List<Employee> employees = scopeService.visibleEmployees(principal);
        List<Holiday> holidays = holidayRepository.findByDate(date);

        Map<UUID, List<WorkEvent>> eventsByEmployee = new HashMap<>();
        for (WorkEvent event : workEventRepository.findAllOnDate(date)) {
            eventsByEmployee.computeIfAbsent(event.getEmployee().getId(), key -> new ArrayList<>()).add(event);
        }

        long present = 0;
        long late = 0;
        long absent = 0;
        long inField = 0;
        long atOffice = 0;
        long finished = 0;
        long pendingActivation = 0;

        for (Employee employee : employees) {
            if (employee.getAccountStatus() == AccountStatus.PENDING_ACTIVATION) {
                pendingActivation++;
            }
            if (employee.getAccountStatus() != AccountStatus.ACTIVE) {
                continue;
            }

            List<WorkEvent> events = eventsByEmployee.getOrDefault(employee.getId(), List.of());
            AttendanceDay day = AttendanceCalculator.compute(
                    employee, employee.getWorkSchedule(), date, events, holidays, settingsService.attendanceRules());

            if (day.statuses().contains(AttendanceStatus.ABSENT)) {
                absent++;
                continue;
            }
            if (day.timeIn() == null) {
                // Rest day or holiday with no activity: not present, not absent.
                continue;
            }

            present++;
            if (day.statuses().contains(AttendanceStatus.LATE)) {
                late++;
            }

            switch (WorkflowEngine.stateFor(events)) {
                case AT_FIELD, EN_ROUTE_TO_FIELD -> inField++;
                case AT_OFFICE, EN_ROUTE_TO_OFFICE -> atOffice++;
                case DAY_ENDED -> finished++;
                default -> { }
            }
        }

        List<StaffEventView> recent = workEventRepository.findRecent(PageRequest.of(0, 12)).stream()
                .filter(event -> inScope(principal, employees, event))
                .map(mapper::toStaffView)
                .toList();

        long requiresReview = countReviewable(principal, employees);

        return new DashboardResponse(
                date,
                employees.size(),
                present,
                late,
                absent,
                inField,
                atOffice,
                finished,
                pendingActivation,
                requiresReview,
                recent,
                monitoringService.latestLocations(principal, date));
    }

    private long countReviewable(AuthPrincipal principal, List<Employee> employees) {
        List<WorkEvent> flagged = workEventRepository.findByReviewStatus(ReviewStatus.REQUIRES_REVIEW);
        if (principal.isAdmin()) {
            return flagged.size();
        }
        List<UUID> ids = employees.stream().map(Employee::getId).toList();
        return flagged.stream().filter(event -> ids.contains(event.getEmployee().getId())).count();
    }

    private boolean inScope(AuthPrincipal principal, List<Employee> employees, WorkEvent event) {
        if (principal.isAdmin()) {
            return true;
        }
        return employees.stream().anyMatch(employee -> employee.getId().equals(event.getEmployee().getId()));
    }
}
