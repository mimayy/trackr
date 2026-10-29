package com.uniace.trackr.dto;

import com.uniace.trackr.domain.enums.AttendanceStatus;
import com.uniace.trackr.domain.enums.Branch;
import com.uniace.trackr.domain.enums.WorkCategory;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Report shapes. Every figure is derived at request time from work events, the
 * employee's work schedule, so a report re-run on the
 * same range always reproduces the same numbers.
 */
public final class ReportDtos {

    private ReportDtos() {
    }

    public record ReportMeta(String reportType, LocalDate from, LocalDate to, int rowCount, Instant generatedAt) {
    }

    public record AttendanceReport(ReportMeta meta, List<AttendanceReportRow> rows, AttendanceTotals totals) {
    }

    public record AttendanceReportRow(
            UUID employeeId,
            String employeeNo,
            String employeeName,
            Branch branch,
            WorkCategory workCategory,
            LocalDate date,
            String scheduleName,
            LocalTime scheduledStart,
            LocalTime scheduledEnd,
            Instant timeIn,
            Instant timeOut,
            double hoursWorked,
            String hoursWorkedLabel,
            long lateMinutes,
            long undertimeMinutes,
            long overtimeMinutes,
            Set<AttendanceStatus> statuses
    ) {
    }

    public record AttendanceTotals(
            double totalHoursWorked,
            long totalLateMinutes,
            long totalUndertimeMinutes,
            long totalOvertimeMinutes,
            long daysPresent,
            long daysAbsent,
            long daysIncomplete
    ) {
    }

    public record LateReport(ReportMeta meta, List<LateReportRow> rows) {
    }

    public record LateReportRow(
            UUID employeeId,
            String employeeNo,
            String employeeName,
            Branch branch,
            LocalDate date,
            LocalTime scheduledStart,
            Instant actualTimeIn,
            LocalTime localTimeIn,
            long minutesLate
    ) {
    }

    public record FieldActivityReport(ReportMeta meta, List<FieldActivityRow> rows) {
    }

    /**
     * One field trip, reconstructed from the arrival and departure the employee
     * recorded. There is no booking to compare it against - the site is
     * whatever they typed on arrival.
     */
    public record FieldActivityRow(
            UUID employeeId,
            String employeeNo,
            String employeeName,
            String siteName,
            LocalDate date,
            Instant arrivalTime,
            Instant departureTime,
            Long minutesOnSite,
            String destination
    ) {
    }

    public record OfficeActivityReport(ReportMeta meta, List<OfficeActivityRow> rows) {
    }

    public record OfficeActivityRow(
            UUID employeeId,
            String employeeNo,
            String employeeName,
            LocalDate date,
            Instant officeDeparture,
            Instant officeArrival,
            Long minutesAway,
            String destination
    ) {
    }

    public record EmployeeActivityReport(
            ReportMeta meta,
            UUID employeeId,
            String employeeName,
            String employeeNo,
            List<WorkEventDtos.StaffEventView> events
    ) {
    }
}
