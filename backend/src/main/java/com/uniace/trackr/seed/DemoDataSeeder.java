package com.uniace.trackr.seed;

import com.uniace.trackr.config.TrackrProperties;
import com.uniace.trackr.domain.*;
import com.uniace.trackr.domain.enums.*;
import com.uniace.trackr.repo.*;
import com.uniace.trackr.util.GeoUtil;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds a realistic Uni-Ace for demonstrations.
 *
 * <p>Everything here is real data in real tables: the dashboard's "present",
 * "late" and "in field" figures are counted from these work events, not
 * hard-coded anywhere. Turn it off for a real deployment with
 * {@code SEED_DEMO_DATA=false}.
 *
 * <p>The generator is seeded with a fixed value, so the same demo comes out the
 * same way every time - which matters when you are showing it to someone.
 */
@Component
@Order(2)
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    /** How many past days of activity to generate. */
    private static final int HISTORY_DAYS = 21;

    /**
     * Employees whose day is left unstarted, so the workflow can be walked
     * through at any hour: one field-based and one
     * office-based. Everyone else gets a realistic finished day, which is what
     * the dashboard and reports are counted from.
     */
    private static final java.util.Set<String> READY_TO_DEMO = java.util.Set.of("EMP-001", "EMP-002");

    private final TrackrProperties properties;
    private final PasswordEncoder passwordEncoder;
    private final UserAccountRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final SupervisorRepository supervisorRepository;
    private final WorkScheduleRepository scheduleRepository;
    private final WorkLocationRepository locationRepository;
    private final WorkEventRepository workEventRepository;
    private final HolidayRepository holidayRepository;

    private final Random random = new Random(20260905L);

    public DemoDataSeeder(TrackrProperties properties,
                          PasswordEncoder passwordEncoder,
                          UserAccountRepository userRepository,
                          EmployeeRepository employeeRepository,
                          SupervisorRepository supervisorRepository,
                          WorkScheduleRepository scheduleRepository,
                          WorkLocationRepository locationRepository,
                          WorkEventRepository workEventRepository,
                          HolidayRepository holidayRepository) {
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.supervisorRepository = supervisorRepository;
        this.scheduleRepository = scheduleRepository;
        this.locationRepository = locationRepository;
        this.workEventRepository = workEventRepository;
        this.holidayRepository = holidayRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.getDemo().isSeed()) {
            return;
        }
        if (employeeRepository.count() > 0) {
            // Already seeded. The history was generated relative to the day it
            // was seeded, so a demo started weeks later would show empty
            // activity lists and a dashboard of zeros. Fill in the days since.
            topUpHistory();
            return;
        }

        log.info("Seeding demo data for Uni-Ace Adjusters Inc.");

        WorkSchedule standard = schedule("Standard Workday");
        WorkSchedule earlyShift = schedule("Field Early Shift");
        WorkSchedule sixDay = schedule("Six-Day Warehouse Shift");

        WorkLocation manilaOffice = location("Manila Office");
        WorkLocation cebuOffice = location("Cebu Office");
        List<WorkLocation> manilaSites = manilaSites();
        List<WorkLocation> cebuSites = cebuSites();

        Supervisor maria = supervisor("Maria", "Santos", "maria.santos@uni-ace.example",
                "+63 917 555 0101", Branch.MANILA, "Manila field and office team");
        Supervisor ramon = supervisor("Ramon", "Villanueva", "ramon.villanueva@uni-ace.example",
                "+63 917 555 0102", Branch.CEBU, "Cebu field and office team");

        List<Employee> employees = new ArrayList<>();
        employees.add(employee("EMP-001", "Juan", "Dela Cruz", "juan.delacruz@uni-ace.example",
                "+63 917 555 0201", Branch.MANILA, WorkCategory.FIELD_BASED, maria, standard, manilaOffice,
                AccountStatus.ACTIVE, LocalDate.of(2019, 3, 4)));
        employees.add(employee("EMP-002", "Ana", "Reyes", "ana.reyes@uni-ace.example",
                "+63 917 555 0202", Branch.MANILA, WorkCategory.OFFICE_BASED, maria, standard, manilaOffice,
                AccountStatus.ACTIVE, LocalDate.of(2017, 8, 15)));
        employees.add(employee("EMP-003", "Carlo", "Cruz", "carlo.cruz@uni-ace.example",
                "+63 917 555 0203", Branch.CEBU, WorkCategory.FIELD_BASED, ramon, earlyShift, cebuOffice,
                AccountStatus.ACTIVE, LocalDate.of(2020, 1, 20)));
        employees.add(employee("EMP-004", "Maria", "Garcia", "maria.garcia@uni-ace.example",
                "+63 917 555 0204", Branch.CEBU, WorkCategory.OFFICE_BASED, ramon, standard, cebuOffice,
                AccountStatus.ACTIVE, LocalDate.of(2021, 6, 1)));
        employees.add(employee("EMP-005", "Roberto", "Mendoza", "roberto.mendoza@uni-ace.example",
                "+63 917 555 0205", Branch.MANILA, WorkCategory.FIELD_BASED, maria, earlyShift, manilaOffice,
                AccountStatus.ACTIVE, LocalDate.of(2016, 11, 7)));
        employees.add(employee("EMP-006", "Luisa", "Bautista", "luisa.bautista@uni-ace.example",
                "+63 917 555 0206", Branch.MANILA, WorkCategory.OFFICE_BASED, maria, standard, manilaOffice,
                AccountStatus.ACTIVE, LocalDate.of(2022, 2, 14)));
        employees.add(employee("EMP-007", "Ernesto", "Lim", "ernesto.lim@uni-ace.example",
                "+63 917 555 0207", Branch.MANILA, WorkCategory.FIELD_BASED, maria, sixDay, manilaOffice,
                AccountStatus.ACTIVE, LocalDate.of(2015, 5, 25)));
        employees.add(employee("EMP-008", "Divina", "Ocampo", "divina.ocampo@uni-ace.example",
                "+63 917 555 0208", Branch.CEBU, WorkCategory.FIELD_BASED, ramon, standard, cebuOffice,
                AccountStatus.ACTIVE, LocalDate.of(2018, 9, 3)));
        employees.add(employee("EMP-009", "Nestor", "Aquino", "nestor.aquino@uni-ace.example",
                "+63 917 555 0209", Branch.MANILA, WorkCategory.OFFICE_BASED, maria, standard, manilaOffice,
                AccountStatus.ACTIVE, LocalDate.of(2023, 4, 17)));

        // One employee who has been created but has not activated yet, so the
        // "Pending Activation" state is visible without having to create one.
        employees.add(employee("EMP-010", "Grace", "Fernandez", "grace.fernandez@uni-ace.example",
                "+63 917 555 0210", Branch.CEBU, WorkCategory.OFFICE_BASED, ramon, standard, cebuOffice,
                AccountStatus.PENDING_ACTIVATION, LocalDate.now().minusDays(2)));

        // ... and one who has left, to show a disabled account.
        employees.add(employee("EMP-011", "Alfredo", "Ramos", "alfredo.ramos@uni-ace.example",
                "+63 917 555 0211", Branch.MANILA, WorkCategory.FIELD_BASED, maria, standard, manilaOffice,
                AccountStatus.DISABLED, LocalDate.of(2014, 7, 9)));

        // Deliberately no seeded device credentials. A placeholder credential
        // makes the admin screens look fuller, but it claims a phone is
        // registered when no key pair exists - so the employee is shown as
        // set up, cannot register for real, and every activity they record is
        // silently unverified. An empty device column is both honest and what
        // a real first day looks like; registering one takes seconds.

        generateHistory(employees, manilaOffice, cebuOffice, manilaSites, cebuSites);

        log.info("Demo data ready: {} employees, {} work events. "
                        + "Every account uses the password from SEED_DEMO_PASSWORD. "
                        + "No devices are registered - sign in as an employee and set up a "
                        + "fingerprint from Profile to try that flow.",
                employeeRepository.count(), workEventRepository.count());
    }

    // -------------------------------------------------------------------------
    // History generation
    // -------------------------------------------------------------------------

    /**
     * Continues each seeded employee's history from their last recorded day up
     * to today, capped at {@link #HISTORY_DAYS}. Employees with no events at
     * all - including anyone created through the admin screens - are left
     * alone, as is any day that already has events.
     */
    private void topUpHistory() {
        ZoneId zone = ZoneId.of(properties.getTimezone());
        LocalDate today = LocalDate.now(zone);
        LocalDate from = today.minusDays(HISTORY_DAYS);

        List<Employee> stale = new ArrayList<>();
        for (Employee employee : employeeRepository.findAll()) {
            LocalDate latest = workEventRepository.findLatestBusinessDate(employee.getId());
            if (latest != null && latest.isBefore(today)) {
                stale.add(employee);
            }
        }
        if (stale.isEmpty()) {
            return;
        }

        log.info("Topping up demo history for {} employees up to {}.", stale.size(), today);
        generateHistory(stale, location("Manila Office"), location("Cebu Office"),
                manilaSites(), cebuSites(), from);
    }

    private void generateHistory(List<Employee> employees,
                                 WorkLocation manilaOffice,
                                 WorkLocation cebuOffice,
                                 List<WorkLocation> manilaSites,
                                 List<WorkLocation> cebuSites) {
        ZoneId zone = ZoneId.of(properties.getTimezone());
        generateHistory(employees, manilaOffice, cebuOffice, manilaSites, cebuSites,
                LocalDate.now(zone).minusDays(HISTORY_DAYS));
    }

    private void generateHistory(List<Employee> employees,
                                 WorkLocation manilaOffice,
                                 WorkLocation cebuOffice,
                                 List<WorkLocation> manilaSites,
                                 List<WorkLocation> cebuSites,
                                 LocalDate from) {

        UserAccount creator = userRepository.findByEmailIgnoreCase(
                        properties.getSecurity().getBootstrapAdminEmail().trim().toLowerCase())
                .orElseGet(() -> userRepository.findByRole(Role.ADMIN).stream().findFirst().orElse(null));

        ZoneId zone = ZoneId.of(properties.getTimezone());
        LocalDate today = LocalDate.now(zone);
        List<Holiday> holidays = holidayRepository.findByDateBetweenOrderByDateAsc(
                from, today);

        for (Employee employee : employees) {
            if (employee.getAccountStatus() != AccountStatus.ACTIVE) {
                continue;
            }
            // Never write over a day that already has events - seeded or real.
            LocalDate latest = workEventRepository.findLatestBusinessDate(employee.getId());

            WorkSchedule schedule = employee.getWorkSchedule();
            WorkLocation office = employee.getBranch() == Branch.MANILA ? manilaOffice : cebuOffice;
            List<WorkLocation> sites = employee.getBranch() == Branch.MANILA ? manilaSites : cebuSites;

            for (int back = (int) Duration.between(from.atStartOfDay(), today.atStartOfDay()).toDays(); back >= 0; back--) {
                LocalDate date = today.minusDays(back);

                if (latest != null && !date.isAfter(latest)) {
                    continue;
                }

                if (!schedule.isWorkingDay(date.getDayOfWeek())) {
                    continue;
                }
                if (holidays.stream().anyMatch(h -> h.getDate().equals(date) && h.appliesTo(employee.getBranch()))) {
                    continue;
                }
                // A believable absence now and then.
                if (back > 0 && random.nextInt(100) < 4) {
                    continue;
                }

                boolean today_ = back == 0;
                boolean startsFresh = today_ && READY_TO_DEMO.contains(employee.getEmployeeNo());

                boolean fieldDay = employee.getWorkCategory() == WorkCategory.FIELD_BASED
                        && (startsFresh || random.nextInt(100) < 65);

                WorkLocation site = fieldDay ? sites.get(random.nextInt(sites.size())) : null;

                if (startsFresh) {
                    // No events for these two today, so whoever is looking at the
                    // demo can actually press TIME IN. Without this, a demo opened
                    // in the evening finds everybody already timed out and nothing
                    // to do.
                    continue;
                }

                generateDay(employee, schedule, zone, date, office, site, today_);
            }
        }
    }

    /**
     * Writes one employee-day. When {@code isToday} is set, only events that
     * would already have happened by now are written, so the dashboard shows
     * people genuinely mid-workday.
     */
    private void generateDay(Employee employee,
                             WorkSchedule schedule,
                             ZoneId zone,
                             LocalDate date,
                             WorkLocation office,
                             WorkLocation site,
                             boolean isToday) {

        Instant cutoff = Instant.now();

        // Most people are on time; some are a little late.
        int lateMinutes = switch (random.nextInt(10)) {
            case 0 -> 12 + random.nextInt(25);
            case 1 -> 3 + random.nextInt(10);
            default -> -random.nextInt(8);
        };

        LocalTime start = schedule.getStartTime().plusMinutes(lateMinutes);
        boolean directToField = site != null && random.nextInt(100) < 25;

        WorkLocation startLocation = directToField ? site : office;
        Instant timeIn = at(date, start, zone);
        if (!write(employee, EventType.TIME_IN, timeIn, startLocation, null, null, isToday, cutoff)) {
            return;
        }

        if (site != null) {
            Instant arrival;

            if (directToField) {
                arrival = timeIn.plusSeconds(60L * (2 + random.nextInt(8)));
            } else {
                Instant departure = at(date, schedule.getStartTime().plusMinutes(25 + random.nextInt(20)), zone);
                if (!write(employee, EventType.OFFICE_DEPARTURE, departure, office, null, null, isToday, cutoff)) {
                    return;
                }
                arrival = departure.plusSeconds(60L * (40 + random.nextInt(45)));
            }

            if (!write(employee, EventType.FIELD_ARRIVAL, arrival, site,
                    site.getName(), null, isToday, cutoff)) {
                return;
            }

            Instant fieldDeparture = arrival.plusSeconds(60L * (150 + random.nextInt(150)));
            boolean returnsToOffice = !directToField || random.nextInt(100) < 40;
            Destination destination = returnsToOffice ? Destination.OFFICE : Destination.HOME;

            if (!write(employee, EventType.FIELD_DEPARTURE, fieldDeparture, site,
                    null, destination, isToday, cutoff)) {
                return;
            }

            if (returnsToOffice) {
                Instant officeArrival = fieldDeparture.plusSeconds(60L * (35 + random.nextInt(40)));
                if (!write(employee, EventType.OFFICE_ARRIVAL, officeArrival, office, null, null, isToday, cutoff)) {
                    return;
                }
                writeTimeOut(employee, schedule, zone, date, office, officeArrival, isToday, cutoff);
            } else {
                write(employee, EventType.TIME_OUT, fieldDeparture.plusSeconds(120),
                        site, null, null, isToday, cutoff);
            }
            return;
        }

        writeTimeOut(employee, schedule, zone, date, office, timeIn, isToday, cutoff);
    }

    private void writeTimeOut(Employee employee,
                              WorkSchedule schedule,
                              ZoneId zone,
                              LocalDate date,
                              WorkLocation office,
                              Instant earliest,
                              boolean isToday,
                              Instant cutoff) {

        // A spread of finishing times: mostly on time, sometimes overtime,
        // occasionally an early finish.
        int endOffset = switch (random.nextInt(12)) {
            case 0, 1 -> 45 + random.nextInt(90);
            case 2 -> -(20 + random.nextInt(40));
            default -> random.nextInt(15);
        };

        Instant timeOut = at(date, schedule.getEndTime().plusMinutes(endOffset), zone);
        if (timeOut.isBefore(earliest)) {
            timeOut = earliest.plusSeconds(3600);
        }
        write(employee, EventType.TIME_OUT, timeOut, office, null, null, isToday, cutoff);
    }

    /**
     * Writes one work event.
     *
     * @return false when the event is in the future relative to now and was
     *         therefore skipped, which tells the caller to stop building the day
     */
    private boolean write(Employee employee,
                          EventType type,
                          Instant when,
                          WorkLocation location,
                          String siteName,
                          Destination destination,
                          boolean isToday,
                          Instant cutoff) {

        if (isToday && when.isAfter(cutoff)) {
            return false;
        }

        // A handful of events are recorded offline, and one of those carries a
        // clock discrepancy, so the exceptions screen has something real in it.
        boolean offline = random.nextInt(100) < 6;
        boolean flagged = offline && random.nextInt(100) < 35;

        double[] position = jitter(location, flagged ? 260 : 35);

        WorkEvent event = new WorkEvent();
        event.setId(UUID.randomUUID());
        event.setEmployee(employee);
        event.setEventType(type);
        event.setEventTime(when);
        event.setDeviceTimestamp(offline ? when.plusSeconds(flagged ? 1500 : 3) : when);
        event.setBusinessDate(when.atZone(ZoneId.of(properties.getTimezone())).toLocalDate());
        event.setLatitude(position[0]);
        event.setLongitude(position[1]);
        event.setGpsAccuracyM(flagged ? 60.0 + random.nextInt(90) : 6.0 + random.nextInt(18));
        event.setLocation(location);
        event.setSiteName(siteName);
        event.setDestination(destination);
        event.setBiometricVerified(!offline || random.nextBoolean());
        event.setCaptureMode(offline ? CaptureMode.OFFLINE : CaptureMode.ONLINE);
        event.setSyncTimestamp(offline ? when.plusSeconds(1800 + random.nextInt(7200)) : when);
        event.setReceivedAt(event.getSyncTimestamp());
        event.setClientAppVersion("1.0.0");

        double distance = GeoUtil.distanceMeters(position[0], position[1],
                location.getLatitude(), location.getLongitude());
        event.setDistanceToLocationM(GeoUtil.round1(distance));
        event.setWithinGeofence(distance <= location.getGeofenceRadiusM());

        if (flagged) {
            event.setDisposition(Disposition.FLAGGED);
            event.setReviewStatus(ReviewStatus.REQUIRES_REVIEW);
            event.setSyncStatus(SyncStatus.FLAGGED);
            event.addFlag(new WorkEventFlag(FlagCategory.OFFLINE_SYNC, "CLOCK_SKEW", FlagSeverity.WARNING,
                    "The device clock differed from the recorded time by 25 minutes."));
            if (!Boolean.TRUE.equals(event.getWithinGeofence())) {
                event.addFlag(new WorkEventFlag(FlagCategory.LOCATION, "OUTSIDE_GEOFENCE", FlagSeverity.WARNING,
                        "Recorded " + Math.round(distance) + " m from " + location.getName() + "."));
            }
        } else {
            event.setSyncStatus(SyncStatus.SYNCED);
        }

        workEventRepository.save(event);
        return true;
    }

    // -------------------------------------------------------------------------
    // Entity creation helpers
    // -------------------------------------------------------------------------


    private Supervisor supervisor(String firstName, String lastName, String email,
                                  String contact, Branch branch, String scopeNote) {
        UserAccount user = new UserAccount();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(properties.getDemo().getPassword()));
        user.setRole(Role.SUPERVISOR);
        user.setStatus(AccountStatus.ACTIVE);
        user.setDisplayName(firstName + " " + lastName);
        userRepository.save(user);

        Supervisor supervisor = new Supervisor();
        supervisor.setUser(user);
        supervisor.setFirstName(firstName);
        supervisor.setLastName(lastName);
        supervisor.setEmail(email);
        supervisor.setContactNumber(contact);
        supervisor.setBranch(branch);
        supervisor.setScopeNote(scopeNote);
        return supervisorRepository.save(supervisor);
    }

    private Employee employee(String employeeNo, String firstName, String lastName, String email,
                              String contact, Branch branch, WorkCategory category,
                              Supervisor supervisor, WorkSchedule schedule, WorkLocation office,
                              AccountStatus status, LocalDate hiredOn) {

        UserAccount user = new UserAccount();
        user.setEmail(email);
        // A pending employee has no password yet - that is the whole point of
        // the activation flow, so the demo reflects it.
        user.setPasswordHash(status == AccountStatus.PENDING_ACTIVATION
                ? null
                : passwordEncoder.encode(properties.getDemo().getPassword()));
        user.setRole(Role.EMPLOYEE);
        user.setStatus(status);
        user.setDisplayName(firstName + " " + lastName);
        userRepository.save(user);

        Employee employee = new Employee();
        employee.setEmployeeNo(employeeNo);
        employee.setUser(user);
        employee.setFirstName(firstName);
        employee.setLastName(lastName);
        employee.setEmail(email);
        employee.setContactNumber(contact);
        employee.setBranch(branch);
        employee.setWorkCategory(category);
        employee.setSupervisor(supervisor);
        employee.setWorkSchedule(schedule);
        employee.setHomeOffice(office);
        employee.setAccountStatus(status);
        employee.setHiredOn(hiredOn);
        return employeeRepository.save(employee);
    }

    private static Instant at(LocalDate date, LocalTime time, ZoneId zone) {
        return ZonedDateTime.of(date, time, zone).toInstant();
    }

    /** A believable GPS reading near a location, within the given radius. */
    private double[] jitter(WorkLocation location, double radiusMeters) {
        double metersPerDegreeLat = 111_320.0;
        double metersPerDegreeLon = metersPerDegreeLat * Math.cos(Math.toRadians(location.getLatitude()));

        double angle = random.nextDouble() * 2 * Math.PI;
        double distance = radiusMeters * Math.sqrt(random.nextDouble());

        return new double[]{
                location.getLatitude() + (distance * Math.cos(angle)) / metersPerDegreeLat,
                location.getLongitude() + (distance * Math.sin(angle)) / metersPerDegreeLon
        };
    }

    private WorkSchedule schedule(String name) {
        return scheduleRepository.findByNameIgnoreCase(name)
                .orElseThrow(() -> new IllegalStateException(
                        "Reference schedule '" + name + "' is missing. Did migration V2 run?"));
    }

    private List<WorkLocation> manilaSites() {
        return List.of(
                location("Warehouse 1"),
                location("Warehouse 2"),
                location("ABC Pharma Distribution Center"),
                location("Northgate Cold Storage"));
    }

    private List<WorkLocation> cebuSites() {
        return List.of(
                location("Warehouse 3"),
                location("Mactan Logistics Yard"));
    }

    private WorkLocation location(String name) {
        Optional<WorkLocation> found = locationRepository.findByNameIgnoreCase(name);
        return found.orElseThrow(() -> new IllegalStateException(
                "Reference location '" + name + "' is missing. Did migration V2 run?"));
    }
}
