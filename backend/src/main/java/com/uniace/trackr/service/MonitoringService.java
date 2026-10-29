package com.uniace.trackr.service;

import com.uniace.trackr.domain.Employee;
import com.uniace.trackr.domain.WorkEvent;
import com.uniace.trackr.domain.enums.*;
import com.uniace.trackr.dto.WorkEventDtos.LatestLocationView;
import com.uniace.trackr.dto.WorkEventDtos.StaffEventView;
import com.uniace.trackr.repo.WorkEventRepository;
import com.uniace.trackr.security.AuthPrincipal;
import com.uniace.trackr.util.DateRanges;
import com.uniace.trackr.service.workflow.WorkflowEngine;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Map and monitoring data.
 *
 * <p>What this deliberately is not: live tracking. Trackr records a position
 * only at the moment a work event happens, so the strongest claim the system
 * can make is "this is where this person was last recorded". Every label in the
 * UI says exactly that.
 */
@Service
public class MonitoringService {

    private final WorkEventRepository workEventRepository;
    private final ScopeService scopeService;
    private final EventMapper mapper;

    public MonitoringService(WorkEventRepository workEventRepository,
                             ScopeService scopeService,
                             EventMapper mapper) {
        this.workEventRepository = workEventRepository;
        this.scopeService = scopeService;
        this.mapper = mapper;
    }

    /** Recorded work-event positions, filtered to what the caller may see. */
    @Transactional(readOnly = true)
    public List<StaffEventView> searchEvents(AuthPrincipal principal,
                                             LocalDate from,
                                             LocalDate to,
                                             UUID employeeId,
                                             Branch branch,
                                             WorkCategory category,
                                             UUID locationId,
                                             EventType eventType) {

        if (employeeId != null) {
            scopeService.requireVisibleEmployee(principal, employeeId);
        }

        boolean scoped = scopeService.isScoped(principal);
        List<UUID> visible = scopeService.visibleEmployeeIds(principal);

        return workEventRepository
                .search(DateRanges.fromOrEarliest(from), DateRanges.toOrLatest(to),
                        employeeId, branch, category, locationId, eventType, scoped, visible)
                .stream()
                .map(mapper::toStaffView)
                .toList();
    }

    /**
     * The latest recorded location for every employee in scope, together with
     * where they are in their workday.
     */
    @Transactional(readOnly = true)
    public List<LatestLocationView> latestLocations(AuthPrincipal principal, LocalDate businessDate) {
        List<Employee> employees = scopeService.visibleEmployees(principal);
        if (employees.isEmpty()) {
            return List.of();
        }

        List<UUID> ids = employees.stream().map(Employee::getId).toList();

        Map<UUID, WorkEvent> latest = new HashMap<>();
        for (WorkEvent event : workEventRepository.findLatestForEmployees(ids)) {
            latest.merge(event.getEmployee().getId(), event,
                    (a, b) -> a.getEventTime().isAfter(b.getEventTime()) ? a : b);
        }

        Map<UUID, List<WorkEvent>> todayByEmployee = new HashMap<>();
        for (WorkEvent event : workEventRepository.findAllOnDate(businessDate)) {
            todayByEmployee.computeIfAbsent(event.getEmployee().getId(), key -> new ArrayList<>()).add(event);
        }

        List<LatestLocationView> views = new ArrayList<>(employees.size());
        for (Employee employee : employees) {
            WorkEvent event = latest.get(employee.getId());
            List<WorkEvent> today = todayByEmployee.getOrDefault(employee.getId(), List.of());
            WorkflowState state = WorkflowEngine.stateFor(today);

            views.add(new LatestLocationView(
                    employee.getId(),
                    employee.getEmployeeNo(),
                    employee.fullName(),
                    employee.getBranch(),
                    employee.getWorkCategory(),
                    employee.getAccountStatus(),
                    event == null ? null : event.getEventType(),
                    event == null ? null : Labels.eventLabel(event.getEventType()),
                    event == null ? null : event.getEventTime(),
                    event == null || event.getLocation() == null ? null : event.getLocation().getName(),
                    event == null ? null : event.getLatitude(),
                    event == null ? null : event.getLongitude(),
                    state,
                    Labels.stateLabel(state)));
        }

        views.sort((a, b) -> a.employeeName().compareToIgnoreCase(b.employeeName()));
        return views;
    }

    /** One employee's timeline for a date, for the supervisor detail view. */
    @Transactional(readOnly = true)
    public List<StaffEventView> timelineFor(AuthPrincipal principal, UUID employeeId, LocalDate date) {
        scopeService.requireVisibleEmployee(principal, employeeId);
        return workEventRepository
                .findByEmployeeIdAndBusinessDateOrderByEventTimeAsc(employeeId, date)
                .stream()
                .map(mapper::toStaffView)
                .toList();
    }
}
