package com.uniace.trackr.service.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniace.trackr.domain.Employee;
import com.uniace.trackr.domain.Holiday;
import com.uniace.trackr.domain.WorkEvent;
import com.uniace.trackr.domain.WorkSchedule;
import com.uniace.trackr.domain.enums.AttendanceStatus;
import com.uniace.trackr.domain.enums.Branch;
import com.uniace.trackr.domain.enums.EventType;
import com.uniace.trackr.domain.enums.WorkCategory;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The attendance rules, worked through end to end.
 *
 * These are the numbers Uni-Ace will check first, so each case here mirrors a
 * scenario from the specification rather than testing the implementation.
 */
class AttendanceCalculatorTest {

    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");
    /** A Wednesday - a working day under every seeded schedule. */
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 9, 9);
    /** The Saturday of the same week. */
    private static final LocalDate SATURDAY = LocalDate.of(2026, 9, 12);

    private final AttendanceRules rules = AttendanceRules.defaults();

    @Nested
    @DisplayName("a standard 8:00-17:00 day with an unpaid 12:00-13:00 lunch")
    class StandardDay {

        @Test
        @DisplayName("8:00 to 17:00 is eight hours worked and on time")
        void onTime() {
            AttendanceDay day = compute(WEDNESDAY, "08:00", "17:00");

            assertThat(day.worked()).isEqualTo(Duration.ofHours(8));
            assertThat(day.hoursWorked()).isEqualTo(8.0);
            assertThat(day.lateMinutes()).isZero();
            assertThat(day.undertimeMinutes()).isZero();
            assertThat(day.overtimeMinutes()).isZero();
            assertThat(day.statuses()).containsExactly(AttendanceStatus.ON_TIME);
        }

        @Test
        @DisplayName("in at 8:30, out at 17:00 is 7h30m: late and undertime")
        void lateAndUndertime() {
            AttendanceDay day = compute(WEDNESDAY, "08:30", "17:00");

            assertThat(day.worked()).isEqualTo(Duration.ofMinutes(450));
            assertThat(day.hoursWorked()).isEqualTo(7.5);
            assertThat(day.lateMinutes()).isEqualTo(30);
            assertThat(day.undertimeMinutes()).isEqualTo(30);
            assertThat(day.overtimeMinutes()).isZero();
            assertThat(day.statuses())
                    .containsExactlyInAnyOrder(AttendanceStatus.LATE, AttendanceStatus.UNDERTIME);
        }

        @Test
        @DisplayName("8:00 to 18:00 is nine hours worked: overtime")
        void overtime() {
            AttendanceDay day = compute(WEDNESDAY, "08:00", "18:00");

            assertThat(day.worked()).isEqualTo(Duration.ofHours(9));
            assertThat(day.overtimeMinutes()).isEqualTo(60);
            assertThat(day.undertimeMinutes()).isZero();
            assertThat(day.statuses())
                    .containsExactlyInAnyOrder(AttendanceStatus.ON_TIME, AttendanceStatus.OVERTIME);
        }

        @Test
        @DisplayName("in at 8:30, out at 18:00 is 8h30m: late and overtime together")
        void lateAndOvertime() {
            AttendanceDay day = compute(WEDNESDAY, "08:30", "18:00");

            assertThat(day.hoursWorked()).isEqualTo(8.5);
            assertThat(day.lateMinutes()).isEqualTo(30);
            assertThat(day.overtimeMinutes()).isEqualTo(30);
            assertThat(day.undertimeMinutes()).isZero();
            assertThat(day.statuses())
                    .containsExactlyInAnyOrder(AttendanceStatus.LATE, AttendanceStatus.OVERTIME);
        }
    }

    @Nested
    @DisplayName("the unpaid break")
    class UnpaidBreak {

        @Test
        @DisplayName("is only deducted for the part of it the employee actually spanned")
        void partialOverlap() {
            // Timed out at 12:20, so only 20 minutes of the 12:00-13:00 break
            // fall inside the worked window. Deducting a full hour here would
            // charge the employee for a lunch they never took.
            AttendanceDay day = compute(WEDNESDAY, "08:00", "12:20");

            assertThat(day.unpaidBreakDeducted()).isEqualTo(Duration.ofMinutes(20));
            assertThat(day.worked()).isEqualTo(Duration.ofHours(4));
        }

        @Test
        @DisplayName("is not deducted at all when the workday ended before it started")
        void noOverlap() {
            AttendanceDay day = compute(WEDNESDAY, "08:00", "11:30");

            assertThat(day.unpaidBreakDeducted()).isZero();
            assertThat(day.worked()).isEqualTo(Duration.ofMinutes(210));
        }

        @Test
        @DisplayName("is not deducted when the schedule says the break is paid")
        void paidBreak() {
            WorkSchedule paid = standardSchedule();
            paid.setBreakPaid(true);

            AttendanceDay day = AttendanceCalculator.compute(
                    employee(), paid, WEDNESDAY,
                    List.of(event(EventType.TIME_IN, WEDNESDAY, "08:00"),
                            event(EventType.TIME_OUT, WEDNESDAY, "17:00")),
                    List.of(), rules);

            assertThat(day.unpaidBreakDeducted()).isZero();
            assertThat(day.worked()).isEqualTo(Duration.ofHours(9));
        }
    }

    @Nested
    @DisplayName("incomplete and unusual days")
    class Unusual {

        @Test
        @DisplayName("a Time In with no Time Out cannot be totalled")
        void noTimeOut() {
            AttendanceDay day = AttendanceCalculator.compute(
                    employee(), standardSchedule(), WEDNESDAY,
                    List.of(event(EventType.TIME_IN, WEDNESDAY, "08:00")),
                    List.of(), rules);

            assertThat(day.complete()).isFalse();
            assertThat(day.worked()).isNull();
            assertThat(day.workedLabel()).isEqualTo("--");
            assertThat(day.statuses()).contains(AttendanceStatus.INCOMPLETE);
        }

        @Test
        @DisplayName("a working day with no events at all is an absence")
        void absent() {
            AttendanceDay day = AttendanceCalculator.compute(
                    employee(), standardSchedule(), WEDNESDAY, List.of(), List.of(), rules);

            assertThat(day.statuses()).containsExactly(AttendanceStatus.ABSENT);
        }

        @Test
        @DisplayName("a rest day with no events is a rest day, not an absence")
        void restDay() {
            AttendanceDay day = AttendanceCalculator.compute(
                    employee(), standardSchedule(), SATURDAY, List.of(), List.of(), rules);

            assertThat(day.restDay()).isTrue();
            assertThat(day.statuses())
                    .containsExactly(AttendanceStatus.REST_DAY)
                    .doesNotContain(AttendanceStatus.ABSENT);
        }

        @Test
        @DisplayName("a holiday with no events is a holiday, not an absence")
        void holiday() {
            Holiday christmas = new Holiday();
            christmas.setDate(WEDNESDAY);
            christmas.setName("Company anniversary");

            AttendanceDay day = AttendanceCalculator.compute(
                    employee(), standardSchedule(), WEDNESDAY, List.of(), List.of(christmas), rules);

            assertThat(day.holiday()).isTrue();
            assertThat(day.holidayName()).isEqualTo("Company anniversary");
            assertThat(day.statuses())
                    .containsExactly(AttendanceStatus.HOLIDAY)
                    .doesNotContain(AttendanceStatus.ABSENT);
        }

        @Test
        @DisplayName("a branch holiday does not apply to the other branch")
        void branchHoliday() {
            Holiday sinulog = new Holiday();
            sinulog.setDate(WEDNESDAY);
            sinulog.setName("Sinulog Festival");
            sinulog.setBranch(Branch.CEBU);

            AttendanceDay manila = AttendanceCalculator.compute(
                    employee(), standardSchedule(), WEDNESDAY, List.of(), List.of(sinulog), rules);

            assertThat(manila.holiday()).isFalse();
            assertThat(manila.statuses()).contains(AttendanceStatus.ABSENT);
        }

        @Test
        @DisplayName("a Time Out recorded before Time In yields no total")
        void timeOutBeforeTimeIn() {
            AttendanceDay day = AttendanceCalculator.compute(
                    employee(), standardSchedule(), WEDNESDAY,
                    List.of(event(EventType.TIME_IN, WEDNESDAY, "17:00"),
                            event(EventType.TIME_OUT, WEDNESDAY, "08:00")),
                    List.of(), rules);

            assertThat(day.complete()).isFalse();
            assertThat(day.worked()).isNull();
        }

        @Test
        @DisplayName("the earliest Time In and the latest Time Out bound the day")
        void duplicateEventsUseOuterBounds() {
            AttendanceDay day = AttendanceCalculator.compute(
                    employee(), standardSchedule(), WEDNESDAY,
                    List.of(event(EventType.TIME_IN, WEDNESDAY, "08:00"),
                            event(EventType.TIME_IN, WEDNESDAY, "09:00"),
                            event(EventType.TIME_OUT, WEDNESDAY, "16:00"),
                            event(EventType.TIME_OUT, WEDNESDAY, "17:00")),
                    List.of(), rules);

            assertThat(day.localTimeIn()).isEqualTo(LocalTime.of(8, 0));
            assertThat(day.localTimeOut()).isEqualTo(LocalTime.of(17, 0));
            assertThat(day.worked()).isEqualTo(Duration.ofHours(8));
        }

        @Test
        @DisplayName("travel between work locations is not deducted")
        void travelCountsAsWork() {
            // 8:00 in, out at 8:30, at the field 9:30, leaves at 14:00, back at
            // the office 15:00, out at 17:00. Only the lunch break comes off.
            AttendanceDay day = AttendanceCalculator.compute(
                    employee(), standardSchedule(), WEDNESDAY,
                    List.of(event(EventType.TIME_IN, WEDNESDAY, "08:00"),
                            event(EventType.OFFICE_DEPARTURE, WEDNESDAY, "08:30"),
                            event(EventType.FIELD_ARRIVAL, WEDNESDAY, "09:30"),
                            event(EventType.FIELD_DEPARTURE, WEDNESDAY, "14:00"),
                            event(EventType.OFFICE_ARRIVAL, WEDNESDAY, "15:00"),
                            event(EventType.TIME_OUT, WEDNESDAY, "17:00")),
                    List.of(), rules);

            assertThat(day.worked()).isEqualTo(Duration.ofHours(8));
            assertThat(day.statuses()).containsExactly(AttendanceStatus.ON_TIME);
        }
    }

    @Nested
    @DisplayName("configurable thresholds")
    class Thresholds {

        @Test
        @DisplayName("a grace period keeps a slightly late start on time")
        void gracePeriod() {
            AttendanceRules withGrace = new AttendanceRules(15, 30, 16);

            AttendanceDay day = AttendanceCalculator.compute(
                    employee(), standardSchedule(), WEDNESDAY,
                    List.of(event(EventType.TIME_IN, WEDNESDAY, "08:10"),
                            event(EventType.TIME_OUT, WEDNESDAY, "17:00")),
                    List.of(), withGrace);

            assertThat(day.lateMinutes()).isZero();
            assertThat(day.statuses()).contains(AttendanceStatus.ON_TIME);
        }

        @Test
        @DisplayName("extra minutes below the overtime threshold are not overtime")
        void belowOvertimeThreshold() {
            AttendanceDay day = compute(WEDNESDAY, "08:00", "17:20");

            assertThat(day.overtimeMinutes()).isZero();
            assertThat(day.statuses()).doesNotContain(AttendanceStatus.OVERTIME);
        }

        @Test
        @DisplayName("nothing is hard-coded: an unusual schedule is honoured")
        void differentSchedule() {
            WorkSchedule earlyShift = new WorkSchedule();
            earlyShift.setName("Field Early Shift");
            earlyShift.setStartTime(LocalTime.of(7, 0));
            earlyShift.setEndTime(LocalTime.of(16, 0));
            earlyShift.setWorkingDaySet(Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY));
            earlyShift.setBreakStart(LocalTime.of(11, 30));
            earlyShift.setBreakEnd(LocalTime.of(12, 30));
            earlyShift.setTimezone("Asia/Manila");

            AttendanceDay day = AttendanceCalculator.compute(
                    employee(), earlyShift, WEDNESDAY,
                    List.of(event(EventType.TIME_IN, WEDNESDAY, "07:00"),
                            event(EventType.TIME_OUT, WEDNESDAY, "16:00")),
                    List.of(), rules);

            assertThat(day.worked()).isEqualTo(Duration.ofHours(8));
            assertThat(day.lateMinutes()).isZero();
            assertThat(day.statuses()).containsExactly(AttendanceStatus.ON_TIME);
        }
    }

    @Nested
    @DisplayName("appointment lateness is a separate measure")
    class AppointmentLateness {

        @Test
        @DisplayName("arriving after the appointment time counts the difference")
        void late() {
            assertThat(AttendanceCalculator.appointmentLateMinutes(
                    LocalTime.of(9, 0), LocalTime.of(9, 17))).isEqualTo(17);
        }

        @Test
        @DisplayName("arriving early or on time is not late")
        void notLate() {
            assertThat(AttendanceCalculator.appointmentLateMinutes(
                    LocalTime.of(9, 0), LocalTime.of(8, 52))).isZero();
            assertThat(AttendanceCalculator.appointmentLateMinutes(
                    LocalTime.of(9, 0), LocalTime.of(9, 0))).isZero();
        }

        @Test
        @DisplayName("appointment lateness never leaks into attendance lateness")
        void independentOfAttendance() {
            // On time for work at 8:00, but 17 minutes late for a 9:00
            // appointment. The attendance figure must stay zero.
            AttendanceDay day = compute(WEDNESDAY, "08:00", "17:00");

            assertThat(day.lateMinutes()).isZero();
            assertThat(AttendanceCalculator.appointmentLateMinutes(
                    LocalTime.of(9, 0), LocalTime.of(9, 17))).isEqualTo(17);
        }
    }

    /* ---------------------------------------------------------------------- */
    /* Fixtures                                                               */
    /* ---------------------------------------------------------------------- */

    private AttendanceDay compute(LocalDate date, String timeIn, String timeOut) {
        return AttendanceCalculator.compute(
                employee(), standardSchedule(), date,
                List.of(event(EventType.TIME_IN, date, timeIn),
                        event(EventType.TIME_OUT, date, timeOut)),
                List.of(), rules);
    }

    private static Employee employee() {
        Employee employee = new Employee();
        employee.setId(UUID.randomUUID());
        employee.setEmployeeNo("EMP-001");
        employee.setFirstName("Juan");
        employee.setLastName("Dela Cruz");
        employee.setBranch(Branch.MANILA);
        employee.setWorkCategory(WorkCategory.FIELD_BASED);
        return employee;
    }

    private static WorkSchedule standardSchedule() {
        WorkSchedule schedule = new WorkSchedule();
        schedule.setName("Standard Workday");
        schedule.setStartTime(LocalTime.of(8, 0));
        schedule.setEndTime(LocalTime.of(17, 0));
        schedule.setWorkingDaySet(Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY));
        schedule.setBreakStart(LocalTime.of(12, 0));
        schedule.setBreakEnd(LocalTime.of(13, 0));
        schedule.setBreakPaid(false);
        schedule.setTimezone("Asia/Manila");
        return schedule;
    }

    private static WorkEvent event(EventType type, LocalDate date, String localTime) {
        WorkEvent event = new WorkEvent();
        event.setId(UUID.randomUUID());
        event.setEventType(type);
        event.setBusinessDate(date);
        event.setEventTime(at(date, localTime));
        return event;
    }

    private static Instant at(LocalDate date, String localTime) {
        String[] parts = localTime.split(":");
        return ZonedDateTime.of(date,
                LocalTime.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])),
                MANILA).toInstant();
    }
}
