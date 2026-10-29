package com.uniace.trackr.support;

import com.uniace.trackr.domain.*;
import com.uniace.trackr.domain.enums.*;
import com.uniace.trackr.repo.*;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the reference data the integration tests need.
 *
 * Tests run against a generated schema with Flyway disabled, so the offices,
 * warehouses and schedules that migration V2 normally seeds are created here
 * instead - with the same names and geofences, so the tests exercise realistic
 * values.
 */
@Component
public class TestFixtures {

    public static final String PASSWORD = "TestPassword123!";

    private final UserAccountRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final SupervisorRepository supervisorRepository;
    private final WorkScheduleRepository scheduleRepository;
    private final WorkLocationRepository locationRepository;
    private final WorkEventRepository workEventRepository;
    private final PasswordEncoder passwordEncoder;

    public TestFixtures(UserAccountRepository userRepository,
                        EmployeeRepository employeeRepository,
                        SupervisorRepository supervisorRepository,
                        WorkScheduleRepository scheduleRepository,
                        WorkLocationRepository locationRepository,
                        WorkEventRepository workEventRepository,
                        PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.supervisorRepository = supervisorRepository;
        this.scheduleRepository = scheduleRepository;
        this.locationRepository = locationRepository;
        this.workEventRepository = workEventRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public WorkSchedule standardSchedule() {
        return scheduleRepository.findByNameIgnoreCase("Standard Workday").orElseGet(() -> {
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
            return scheduleRepository.save(schedule);
        });
    }

    @Transactional
    public WorkLocation manilaOffice() {
        return location("Manila Office", LocationType.OFFICE, Branch.MANILA, 14.554729, 121.024445, 120);
    }

    @Transactional
    public WorkLocation warehouseTwo() {
        return location("Warehouse 2", LocationType.WAREHOUSE, Branch.MANILA, 14.479500, 121.019800, 150);
    }

    @Transactional
    public WorkLocation location(String name, LocationType type, Branch branch,
                                 double latitude, double longitude, int radius) {
        return locationRepository.findByNameIgnoreCase(name).orElseGet(() -> {
            WorkLocation location = new WorkLocation();
            location.setName(name);
            location.setType(type);
            location.setBranch(branch);
            location.setLatitude(latitude);
            location.setLongitude(longitude);
            location.setGeofenceRadiusM(radius);
            location.setActive(true);
            return locationRepository.save(location);
        });
    }

    @Transactional
    public UserAccount user(String email, Role role, String displayName, AccountStatus status) {
        return userRepository.findByEmailIgnoreCase(email).orElseGet(() -> {
            UserAccount user = new UserAccount();
            user.setEmail(email);
            user.setRole(role);
            user.setDisplayName(displayName);
            user.setStatus(status);
            user.setPasswordHash(status == AccountStatus.PENDING_ACTIVATION
                    ? null
                    : passwordEncoder.encode(PASSWORD));
            return userRepository.save(user);
        });
    }

    @Transactional
    public Supervisor supervisor(String firstName, String lastName, String email, Branch branch) {
        return supervisorRepository.findByEmailIgnoreCase(email).orElseGet(() -> {
            Supervisor supervisor = new Supervisor();
            supervisor.setUser(user(email, Role.SUPERVISOR, firstName + " " + lastName, AccountStatus.ACTIVE));
            supervisor.setFirstName(firstName);
            supervisor.setLastName(lastName);
            supervisor.setEmail(email);
            supervisor.setBranch(branch);
            return supervisorRepository.save(supervisor);
        });
    }

    @Transactional
    public Employee employee(String employeeNo,
                             String firstName,
                             String lastName,
                             String email,
                             Branch branch,
                             WorkCategory category,
                             Supervisor supervisor,
                             AccountStatus status) {
        return employeeRepository.findByEmployeeNoIgnoreCase(employeeNo).orElseGet(() -> {
            Employee employee = new Employee();
            employee.setEmployeeNo(employeeNo);
            employee.setUser(user(email, Role.EMPLOYEE, firstName + " " + lastName, status));
            employee.setFirstName(firstName);
            employee.setLastName(lastName);
            employee.setEmail(email);
            employee.setBranch(branch);
            employee.setWorkCategory(category);
            employee.setSupervisor(supervisor);
            employee.setWorkSchedule(standardSchedule());
            employee.setHomeOffice(manilaOffice());
            employee.setAccountStatus(status);
            employee.setHiredOn(LocalDate.of(2020, 1, 1));
            return employeeRepository.save(employee);
        });
    }

    /** A minimal recorded event, for tests that only need one to exist. */
    @Transactional
    public WorkEvent workEvent(Employee employee, WorkLocation location) {
        WorkEvent event = new WorkEvent();
        event.setId(UUID.randomUUID());
        event.setEmployee(employee);
        event.setEventType(EventType.TIME_IN);
        event.setEventTime(Instant.now());
        event.setBusinessDate(LocalDate.now(ZoneId.of("Asia/Manila")));
        event.setLocation(location);
        event.setLatitude(location.getLatitude());
        event.setLongitude(location.getLongitude());
        event.setBiometricVerified(false);
        event.setCaptureMode(CaptureMode.ONLINE);
        event.setSyncStatus(SyncStatus.SYNCED);
        event.setSyncTimestamp(Instant.now());
        event.setReceivedAt(Instant.now());
        return workEventRepository.save(event);
    }
}
