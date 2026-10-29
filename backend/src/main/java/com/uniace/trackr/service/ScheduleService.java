package com.uniace.trackr.service;

import com.uniace.trackr.domain.Holiday;
import com.uniace.trackr.domain.WorkSchedule;
import com.uniace.trackr.dto.AdminDtos.HolidayRequest;
import com.uniace.trackr.dto.AdminDtos.HolidayRow;
import com.uniace.trackr.dto.AdminDtos.ScheduleRequest;
import com.uniace.trackr.dto.AdminDtos.ScheduleRow;
import com.uniace.trackr.repo.EmployeeRepository;
import com.uniace.trackr.repo.HolidayRepository;
import com.uniace.trackr.repo.WorkScheduleRepository;
import com.uniace.trackr.security.AuthPrincipal;
import com.uniace.trackr.web.error.ApiException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Work schedules, working days, breaks and non-working dates. */
@Service
public class ScheduleService {

    private final WorkScheduleRepository scheduleRepository;
    private final HolidayRepository holidayRepository;
    private final EmployeeRepository employeeRepository;
    private final AuditService auditService;

    public ScheduleService(WorkScheduleRepository scheduleRepository,
                           HolidayRepository holidayRepository,
                           EmployeeRepository employeeRepository,
                           AuditService auditService) {
        this.scheduleRepository = scheduleRepository;
        this.holidayRepository = holidayRepository;
        this.employeeRepository = employeeRepository;
        this.auditService = auditService;
    }

    // -------------------------------------------------------------------------
    // Schedules
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ScheduleRow> list() {
        return scheduleRepository.findAllByOrderByNameAsc().stream().map(this::toRow).toList();
    }

    @Transactional(readOnly = true)
    public WorkSchedule require(UUID id) {
        return scheduleRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("That work schedule could not be found."));
    }

    @Transactional
    public WorkSchedule create(AuthPrincipal actor, ScheduleRequest request) {
        scheduleRepository.findByNameIgnoreCase(request.name().trim()).ifPresent(existing -> {
            throw ApiException.conflict("SCHEDULE_NAME_TAKEN", "A schedule with that name already exists.");
        });

        WorkSchedule schedule = new WorkSchedule();
        apply(schedule, request);
        WorkSchedule saved = scheduleRepository.save(schedule);

        auditService.success(actor, "CREATE_SCHEDULE", "WorkSchedule", saved.getId(), saved.getName(),
                describe(saved));
        return saved;
    }

    @Transactional
    public WorkSchedule update(AuthPrincipal actor, UUID id, ScheduleRequest request) {
        WorkSchedule schedule = require(id);
        String before = describe(schedule);

        apply(schedule, request);
        WorkSchedule saved = scheduleRepository.save(schedule);

        auditService.success(actor, "UPDATE_SCHEDULE", "WorkSchedule", saved.getId(), saved.getName(),
                "Was: " + before + " | Now: " + describe(saved));
        return saved;
    }

    private void apply(WorkSchedule schedule, ScheduleRequest request) {
        if (!request.endTime().isAfter(request.startTime())) {
            throw ApiException.badRequest("SCHEDULE_TIMES_INVALID",
                    "The end time must be after the start time.");
        }
        if ((request.breakStart() == null) != (request.breakEnd() == null)) {
            throw ApiException.badRequest("SCHEDULE_BREAK_INVALID",
                    "Please set both a break start and a break end, or neither.");
        }
        if (request.breakStart() != null && !request.breakEnd().isAfter(request.breakStart())) {
            throw ApiException.badRequest("SCHEDULE_BREAK_INVALID",
                    "The break must end after it starts.");
        }

        Set<DayOfWeek> days;
        try {
            days = request.workingDays().stream()
                    .map(day -> DayOfWeek.valueOf(day.trim().toUpperCase()))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("SCHEDULE_DAYS_INVALID", "Please choose valid working days.");
        }

        String timezone = request.timezone() == null || request.timezone().isBlank()
                ? "Asia/Manila"
                : request.timezone().trim();
        try {
            ZoneId.of(timezone);
        } catch (RuntimeException ex) {
            throw ApiException.badRequest("SCHEDULE_TIMEZONE_INVALID", "That timezone is not recognised.");
        }

        schedule.setName(request.name().trim());
        schedule.setStartTime(request.startTime());
        schedule.setEndTime(request.endTime());
        schedule.setWorkingDaySet(days);
        schedule.setBreakStart(request.breakStart());
        schedule.setBreakEnd(request.breakEnd());
        schedule.setBreakPaid(Boolean.TRUE.equals(request.breakPaid()));
        schedule.setTimezone(timezone);
        schedule.setActive(request.active() == null || request.active());
    }

    @Transactional(readOnly = true)
    public ScheduleRow toRow(WorkSchedule schedule) {
        long employeeCount = employeeRepository.findAll().stream()
                .filter(employee -> employee.getWorkSchedule() != null
                        && employee.getWorkSchedule().getId().equals(schedule.getId()))
                .count();

        return new ScheduleRow(
                schedule.getId(),
                schedule.getName(),
                schedule.getStartTime(),
                schedule.getEndTime(),
                schedule.workingDaySet().stream().map(Enum::name).collect(Collectors.toCollection(LinkedHashSet::new)),
                schedule.getBreakStart(),
                schedule.getBreakEnd(),
                schedule.isBreakPaid(),
                schedule.getTimezone(),
                schedule.isActive(),
                Math.round(schedule.scheduledWorkDuration().toMinutes() / 60.0 * 100.0) / 100.0,
                employeeCount);
    }

    // -------------------------------------------------------------------------
    // Holidays / non-working dates
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<HolidayRow> listHolidays() {
        return holidayRepository.findAllByOrderByDateAsc().stream()
                .map(h -> new HolidayRow(h.getId(), h.getDate(), h.getName(), h.getBranch(), h.isPaid()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Holiday> holidaysBetween(LocalDate from, LocalDate to) {
        return holidayRepository.findByDateBetweenOrderByDateAsc(from, to);
    }

    @Transactional
    public Holiday createHoliday(AuthPrincipal actor, HolidayRequest request) {
        Holiday holiday = new Holiday();
        holiday.setDate(request.date());
        holiday.setName(request.name().trim());
        holiday.setBranch(request.branch());
        holiday.setPaid(request.paid() == null || request.paid());

        Holiday saved = holidayRepository.save(holiday);
        auditService.success(actor, "CREATE_HOLIDAY", "Holiday", saved.getId(), saved.getName(),
                saved.getDate().toString());
        return saved;
    }

    @Transactional
    public void deleteHoliday(AuthPrincipal actor, UUID id) {
        Holiday holiday = holidayRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("That non-working date could not be found."));
        holidayRepository.delete(holiday);
        auditService.success(actor, "DELETE_HOLIDAY", "Holiday", id, holiday.getName(),
                holiday.getDate().toString());
    }

    private static String describe(WorkSchedule schedule) {
        return schedule.getStartTime() + "-" + schedule.getEndTime()
                + " on " + schedule.getWorkingDays()
                + (schedule.getBreakStart() == null
                        ? " with no break"
                        : ", break " + schedule.getBreakStart() + "-" + schedule.getBreakEnd()
                          + (schedule.isBreakPaid() ? " (paid)" : " (unpaid)"));
    }
}
