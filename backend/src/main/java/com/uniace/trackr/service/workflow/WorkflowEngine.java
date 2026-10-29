package com.uniace.trackr.service.workflow;

import com.uniace.trackr.domain.WorkEvent;
import com.uniace.trackr.domain.enums.Destination;
import com.uniace.trackr.domain.enums.EventType;
import com.uniace.trackr.domain.enums.LocationType;
import com.uniace.trackr.domain.enums.WorkflowState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Decides where an employee is in their workday and which action may come next.
 *
 * <p>This is the authority on valid sequences. The employee UI shows one button
 * because this class says only one action is possible - not the other way
 * round. Every event submission is re-checked here on the server, so hiding a
 * button is a convenience, never a control.
 *
 * <p>Supported sequences:
 * <pre>
 *   office day    TIME_IN -> TIME_OUT
 *   office/field  TIME_IN -> OFFICE_DEPARTURE -> FIELD_ARRIVAL -> FIELD_DEPARTURE
 *                         -> OFFICE_ARRIVAL -> TIME_OUT
 *   direct field  TIME_IN -> FIELD_ARRIVAL -> FIELD_DEPARTURE -> TIME_OUT
 * </pre>
 * Multiple field cycles in one day are allowed: a Field Departure headed for
 * another field location returns the employee to the "on the way to the field"
 * state.
 */
public final class WorkflowEngine {

    private WorkflowEngine() {
    }

    /** Derives the current state from the day's events, oldest first. */
    public static WorkflowState stateFor(List<WorkEvent> eventsToday) {
        WorkflowState state = WorkflowState.NOT_STARTED;

        List<WorkEvent> ordered = new ArrayList<>(eventsToday);
        ordered.sort(Comparator.comparing(WorkEvent::getEventTime));

        for (WorkEvent event : ordered) {
            state = switch (event.getEventType()) {
                case TIME_IN -> startedAtField(event) ? WorkflowState.EN_ROUTE_TO_FIELD : WorkflowState.AT_OFFICE;
                case OFFICE_DEPARTURE -> WorkflowState.EN_ROUTE_TO_FIELD;
                case FIELD_ARRIVAL -> WorkflowState.AT_FIELD;
                case FIELD_DEPARTURE -> afterFieldDeparture(event.getDestination());
                case OFFICE_ARRIVAL -> WorkflowState.AT_OFFICE;
                case TIME_OUT -> WorkflowState.DAY_ENDED;
            };
        }

        return state;
    }

    /**
     * Which events the employee may record right now.
     *
     * <p>Leaving the office no longer depends on a booked assignment. Uni-Ace's
     * warehouses are not a fixed list and visits are not scheduled in advance,
     * so an employee is trusted to say where they are going and the site name is
     * captured at Field Arrival instead.
     */
    public static List<EventType> allowedActions(WorkflowState state) {
        return switch (state) {
            case NOT_STARTED -> List.of(EventType.TIME_IN);
            case AT_OFFICE -> List.of(EventType.OFFICE_DEPARTURE, EventType.TIME_OUT);
            case EN_ROUTE_TO_FIELD -> List.of(EventType.FIELD_ARRIVAL);
            case AT_FIELD -> List.of(EventType.FIELD_DEPARTURE);
            case EN_ROUTE_TO_OFFICE -> List.of(EventType.OFFICE_ARRIVAL);
            case EN_ROUTE_HOME -> List.of(EventType.TIME_OUT);
            case DAY_ENDED -> List.of();
        };
    }

    /**
     * Checks whether an event may be recorded from the current state.
     *
     * @return empty when the event is valid, otherwise a message written for the
     *         employee explaining what is expected instead
     */
    public static Optional<String> validate(WorkflowState state, EventType requested) {
        if (allowedActions(state).contains(requested)) {
            return Optional.empty();
        }

        // Tapping Time In twice is the commonest mistake, and it deserves an
        // answer about that rather than a general description of the state.
        if (requested == EventType.TIME_IN) {
            return Optional.of(state == WorkflowState.DAY_ENDED
                    ? "You have already finished your workday today."
                    : "You have already timed in today.");
        }

        return Optional.of(switch (state) {
            case NOT_STARTED -> "Please Time In first to start your workday.";
            case AT_OFFICE -> "You are at the office. You can leave for the field or Time Out.";
            case EN_ROUTE_TO_FIELD -> "You are on your way. Record your arrival when you get there.";
            case AT_FIELD -> "You are at the site. Record Field Departure when you are finished.";
            case EN_ROUTE_TO_OFFICE -> "You are on your way back to the office. Please record Office Arrival first.";
            case EN_ROUTE_HOME -> "You are heading home. The only thing left today is Time Out.";
            case DAY_ENDED -> "You have already timed out for today.";
        });
    }

    /** Whether a Time Out would leave a return-to-office trip unfinished. */
    public static boolean blocksTimeOut(WorkflowState state) {
        return state == WorkflowState.AT_FIELD
                || state == WorkflowState.EN_ROUTE_TO_OFFICE
                || state == WorkflowState.EN_ROUTE_TO_FIELD;
    }

    private static WorkflowState afterFieldDeparture(Destination destination) {
        if (destination == null) {
            // Destination is mandatory when recording a Field Departure, so this
            // only happens for legacy or repaired rows. Heading home is the
            // conservative reading: it still requires an explicit Time Out.
            return WorkflowState.EN_ROUTE_HOME;
        }
        return switch (destination) {
            case OFFICE -> WorkflowState.EN_ROUTE_TO_OFFICE;
            case HOME -> WorkflowState.EN_ROUTE_HOME;
            case ANOTHER_FIELD_LOCATION -> WorkflowState.EN_ROUTE_TO_FIELD;
        };
    }

    /**
     * A Time In recorded at a warehouse or field site means the employee went
     * straight to the field; their next step is to confirm arrival there.
     */
    private static boolean startedAtField(WorkEvent timeIn) {
        return timeIn.getLocation() != null && timeIn.getLocation().getType() != LocationType.OFFICE;
    }
}
