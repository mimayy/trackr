package com.uniace.trackr.service.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniace.trackr.domain.WorkEvent;
import com.uniace.trackr.domain.WorkLocation;
import com.uniace.trackr.domain.enums.Destination;
import com.uniace.trackr.domain.enums.EventType;
import com.uniace.trackr.domain.enums.LocationType;
import com.uniace.trackr.domain.enums.WorkflowState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The event sequence rules.
 *
 * This is what guarantees the employee only ever sees one sensible button, and
 * what stops an impossible sequence being accepted by an edited request.
 */
class WorkflowEngineTest {

    private static final Instant BASE = Instant.parse("2026-09-09T00:00:00Z");

    @Nested
    @DisplayName("the office workday")
    class OfficeDay {

        @Test
        @DisplayName("starts with Time In and nothing else")
        void beforeTimeIn() {
            assertThat(WorkflowEngine.stateFor(List.of())).isEqualTo(WorkflowState.NOT_STARTED);
            assertThat(WorkflowEngine.allowedActions(WorkflowState.NOT_STARTED))
                    .containsExactly(EventType.TIME_IN);
        }

        @Test
        @DisplayName("goes Time In then Time Out for someone who stays in")
        void timeInThenTimeOut() {
            List<WorkEvent> day = new ArrayList<>(List.of(timeIn(office(), 0)));

            assertThat(WorkflowEngine.stateFor(day)).isEqualTo(WorkflowState.AT_OFFICE);
            assertThat(WorkflowEngine.allowedActions(WorkflowState.AT_OFFICE))
                    .contains(EventType.TIME_OUT);

            day.add(event(EventType.TIME_OUT, office(), 9));
            assertThat(WorkflowEngine.stateFor(day)).isEqualTo(WorkflowState.DAY_ENDED);
            assertThat(WorkflowEngine.allowedActions(WorkflowState.DAY_ENDED)).isEmpty();
        }

        @Test
        @DisplayName("always offers leaving the office, with nothing to book first")
        void departureNeedsNoBooking() {
            // Visits are not scheduled in advance: an employee can be sent to a
            // warehouse at any moment, and says which one when they arrive.
            assertThat(WorkflowEngine.allowedActions(WorkflowState.AT_OFFICE))
                    .containsExactly(EventType.OFFICE_DEPARTURE, EventType.TIME_OUT);
        }
    }

    @Nested
    @DisplayName("office to field and back")
    class OfficeFieldOffice {

        @Test
        @DisplayName("walks the whole sequence one state at a time")
        void fullCycle() {
            List<WorkEvent> day = new ArrayList<>();

            day.add(timeIn(office(), 0));
            assertThat(WorkflowEngine.stateFor(day)).isEqualTo(WorkflowState.AT_OFFICE);

            day.add(event(EventType.OFFICE_DEPARTURE, office(), 1));
            assertThat(WorkflowEngine.stateFor(day)).isEqualTo(WorkflowState.EN_ROUTE_TO_FIELD);
            assertThat(WorkflowEngine.allowedActions(WorkflowState.EN_ROUTE_TO_FIELD))
                    .containsExactly(EventType.FIELD_ARRIVAL);

            day.add(event(EventType.FIELD_ARRIVAL, warehouse(), 2));
            assertThat(WorkflowEngine.stateFor(day)).isEqualTo(WorkflowState.AT_FIELD);
            assertThat(WorkflowEngine.allowedActions(WorkflowState.AT_FIELD))
                    .containsExactly(EventType.FIELD_DEPARTURE);

            day.add(departure(Destination.OFFICE, 6));
            assertThat(WorkflowEngine.stateFor(day)).isEqualTo(WorkflowState.EN_ROUTE_TO_OFFICE);
            assertThat(WorkflowEngine.allowedActions(WorkflowState.EN_ROUTE_TO_OFFICE))
                    .containsExactly(EventType.OFFICE_ARRIVAL);

            day.add(event(EventType.OFFICE_ARRIVAL, office(), 7));
            assertThat(WorkflowEngine.stateFor(day)).isEqualTo(WorkflowState.AT_OFFICE);

            day.add(event(EventType.TIME_OUT, office(), 9));
            assertThat(WorkflowEngine.stateFor(day)).isEqualTo(WorkflowState.DAY_ENDED);
        }

        @Test
        @DisplayName("orders events by time even when they arrive out of order")
        void sortsByTime() {
            // An offline batch can be uploaded in any order; the state must be
            // rebuilt from the times, not from the order of the list.
            List<WorkEvent> shuffled = List.of(
                    event(EventType.FIELD_ARRIVAL, warehouse(), 2),
                    timeIn(office(), 0),
                    event(EventType.OFFICE_DEPARTURE, office(), 1));

            assertThat(WorkflowEngine.stateFor(shuffled)).isEqualTo(WorkflowState.AT_FIELD);
        }
    }

    @Nested
    @DisplayName("going straight to the field")
    class DirectToField {

        @Test
        @DisplayName("a Time In at a warehouse expects a Field Arrival next, not an office departure")
        void timeInAtWarehouse() {
            List<WorkEvent> day = new ArrayList<>(List.of(timeIn(warehouse(), 0)));

            assertThat(WorkflowEngine.stateFor(day)).isEqualTo(WorkflowState.EN_ROUTE_TO_FIELD);
            assertThat(WorkflowEngine.allowedActions(WorkflowState.EN_ROUTE_TO_FIELD))
                    .containsExactly(EventType.FIELD_ARRIVAL);

            day.add(event(EventType.FIELD_ARRIVAL, warehouse(), 1));
            day.add(departure(Destination.HOME, 8));

            assertThat(WorkflowEngine.stateFor(day)).isEqualTo(WorkflowState.EN_ROUTE_HOME);
            assertThat(WorkflowEngine.allowedActions(WorkflowState.EN_ROUTE_HOME))
                    .containsExactly(EventType.TIME_OUT);
        }

        @Test
        @DisplayName("a Time In with no recognised location is treated as the office")
        void unknownLocation() {
            assertThat(WorkflowEngine.stateFor(List.of(timeIn(null, 0))))
                    .isEqualTo(WorkflowState.AT_OFFICE);
        }
    }

    @Nested
    @DisplayName("several field visits in one day")
    class MultipleFieldCycles {

        @Test
        @DisplayName("leaving for another field location returns to the on-the-way state")
        void anotherFieldLocation() {
            List<WorkEvent> day = new ArrayList<>(List.of(
                    timeIn(office(), 0),
                    event(EventType.OFFICE_DEPARTURE, office(), 1),
                    event(EventType.FIELD_ARRIVAL, warehouse(), 2),
                    departure(Destination.ANOTHER_FIELD_LOCATION, 4)));

            assertThat(WorkflowEngine.stateFor(day)).isEqualTo(WorkflowState.EN_ROUTE_TO_FIELD);

            day.add(event(EventType.FIELD_ARRIVAL, warehouse(), 5));
            assertThat(WorkflowEngine.stateFor(day)).isEqualTo(WorkflowState.AT_FIELD);
        }
    }

    @Nested
    @DisplayName("invalid sequences")
    class Invalid {

        @Test
        @DisplayName("Time Out before Time In is refused with an instruction, not an error code")
        void timeOutFirst() {
            var problem = WorkflowEngine.validate(WorkflowState.NOT_STARTED, EventType.TIME_OUT);

            assertThat(problem).isPresent();
            assertThat(problem.get()).isEqualTo("Please Time In first to start your workday.");
        }

        @Test
        @DisplayName("a second Time In is refused, and says so rather than describing the state")
        void duplicateTimeIn() {
            assertThat(WorkflowEngine.validate(WorkflowState.AT_OFFICE, EventType.TIME_IN))
                    .contains("You have already timed in today.");
            assertThat(WorkflowEngine.validate(WorkflowState.AT_FIELD, EventType.TIME_IN))
                    .contains("You have already timed in today.");
            assertThat(WorkflowEngine.validate(WorkflowState.DAY_ENDED, EventType.TIME_IN))
                    .contains("You have already finished your workday today.");
        }

        @Test
        @DisplayName("Field Departure before Field Arrival is refused")
        void departureBeforeArrival() {
            assertThat(WorkflowEngine.validate(
                    WorkflowState.EN_ROUTE_TO_FIELD, EventType.FIELD_DEPARTURE)).isPresent();
        }

        @Test
        @DisplayName("Time Out is refused while a return to the office is unfinished")
        void timeOutDuringReturn() {
            assertThat(WorkflowEngine.validate(
                    WorkflowState.EN_ROUTE_TO_OFFICE, EventType.TIME_OUT)).isPresent();
            assertThat(WorkflowEngine.blocksTimeOut(WorkflowState.EN_ROUTE_TO_OFFICE)).isTrue();
            assertThat(WorkflowEngine.blocksTimeOut(WorkflowState.AT_FIELD)).isTrue();
            assertThat(WorkflowEngine.blocksTimeOut(WorkflowState.EN_ROUTE_HOME)).isFalse();
        }

        @Test
        @DisplayName("nothing at all is allowed once the day has ended")
        void afterTimeOut() {
            for (EventType type : EventType.values()) {
                assertThat(WorkflowEngine.validate(WorkflowState.DAY_ENDED, type))
                        .as("event %s after Time Out", type)
                        .isPresent();
            }
        }

        @Test
        @DisplayName("a valid event passes validation with no message")
        void validEvent() {
            assertThat(WorkflowEngine.validate(WorkflowState.NOT_STARTED, EventType.TIME_IN))
                    .isEmpty();
        }
    }

    /* ---------------------------------------------------------------------- */
    /* Fixtures                                                               */
    /* ---------------------------------------------------------------------- */

    private static WorkEvent timeIn(WorkLocation location, int hoursFromBase) {
        return event(EventType.TIME_IN, location, hoursFromBase);
    }

    private static WorkEvent departure(Destination destination, int hoursFromBase) {
        WorkEvent event = event(EventType.FIELD_DEPARTURE, warehouse(), hoursFromBase);
        event.setDestination(destination);
        return event;
    }

    private static WorkEvent event(EventType type, WorkLocation location, int hoursFromBase) {
        WorkEvent event = new WorkEvent();
        event.setId(UUID.randomUUID());
        event.setEventType(type);
        event.setLocation(location);
        event.setEventTime(BASE.plusSeconds(3600L * hoursFromBase));
        return event;
    }

    private static WorkLocation office() {
        return location("Manila Office", LocationType.OFFICE);
    }

    private static WorkLocation warehouse() {
        return location("Warehouse 2", LocationType.WAREHOUSE);
    }

    private static WorkLocation location(String name, LocationType type) {
        WorkLocation location = new WorkLocation();
        location.setId(UUID.randomUUID());
        location.setName(name);
        location.setType(type);
        return location;
    }
}
