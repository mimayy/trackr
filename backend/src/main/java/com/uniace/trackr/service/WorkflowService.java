package com.uniace.trackr.service;

import com.uniace.trackr.domain.Employee;
import com.uniace.trackr.domain.WorkEvent;
import com.uniace.trackr.domain.enums.EventType;
import com.uniace.trackr.domain.enums.WorkflowState;
import com.uniace.trackr.dto.WorkEventDtos.ActionOption;
import com.uniace.trackr.dto.WorkEventDtos.WorkflowStatusResponse;
import com.uniace.trackr.service.workflow.WorkflowEngine;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the employee's home screen: their current state and the one action
 * that makes sense next.
 *
 * <p>The server decides which actions exist. The phone renders what it is told,
 * which is why the employee never sees six buttons and has to work out which
 * one applies to them.
 */
@Service
public class WorkflowService {

    private final WorkEventService workEventService;
    private final WebAuthnService webAuthnService;
    private final EventMapper mapper;

    public WorkflowService(WorkEventService workEventService,
                           WebAuthnService webAuthnService,
                           EventMapper mapper) {
        this.workEventService = workEventService;
        this.webAuthnService = webAuthnService;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public WorkflowStatusResponse statusFor(Employee employee) {
        LocalDate today = LocalDate.now(workEventService.scheduleZone(employee));
        return statusFor(employee, today);
    }

    @Transactional(readOnly = true)
    public WorkflowStatusResponse statusFor(Employee employee, LocalDate date) {
        List<WorkEvent> events = workEventService.eventsForDay(employee, date);
        WorkflowState state = WorkflowEngine.stateFor(events);

        List<EventType> allowed = WorkflowEngine.allowedActions(state);
        List<ActionOption> options = allowed.stream().map(WorkflowService::toOption).toList();

        Instant startedAt = events.stream()
                .filter(e -> e.getEventType() == EventType.TIME_IN)
                .map(WorkEvent::getEventTime)
                .min(Instant::compareTo)
                .orElse(null);

        return new WorkflowStatusResponse(
                state,
                Labels.stateLabel(state),
                Labels.stateDetail(state),
                startedAt,
                currentLocationName(events),
                options,
                options.isEmpty() ? null : options.get(0),
                allowed.contains(EventType.FIELD_DEPARTURE),
                webAuthnService.activeCredential(employee).isPresent(),
                true,
                workEventService.recentSiteNames(employee),
                events.stream().map(mapper::toEmployeeView).toList());
    }

    /**
     * The location of the most recent event that had one - described as where
     * the employee was last recorded, never as where they are now.
     */
    private static String currentLocationName(List<WorkEvent> events) {
        for (int i = events.size() - 1; i >= 0; i--) {
            WorkEvent event = events.get(i);
            if (event.getLocation() != null) {
                return event.getLocation().getName();
            }
        }
        return null;
    }

    private static ActionOption toOption(EventType type) {
        return new ActionOption(
                type,
                Labels.actionLabel(type),
                Labels.actionHelper(type),
                true,
                type == EventType.FIELD_ARRIVAL);
    }

    /** Used by monitoring screens to describe an employee's state in one word. */
    public static Optional<WorkflowState> stateOf(List<WorkEvent> eventsToday) {
        return eventsToday.isEmpty() ? Optional.empty() : Optional.of(WorkflowEngine.stateFor(eventsToday));
    }
}
