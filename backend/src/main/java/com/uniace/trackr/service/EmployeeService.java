package com.uniace.trackr.service;

import com.uniace.trackr.domain.*;
import com.uniace.trackr.domain.enums.*;
import com.uniace.trackr.dto.AdminDtos.*;
import com.uniace.trackr.repo.*;
import com.uniace.trackr.security.AuthPrincipal;
import com.uniace.trackr.web.error.ApiException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Employee records: creation, editing, enabling, disabling and device security. */
@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final UserAccountRepository userRepository;
    private final SupervisorRepository supervisorRepository;
    private final WorkScheduleRepository scheduleRepository;
    private final WorkLocationRepository locationRepository;
    private final DeviceCredentialRepository credentialRepository;
    private final ActivationTokenRepository activationTokenRepository;
    private final WorkEventRepository workEventRepository;
    private final LocationService locationService;
    private final AuditService auditService;
    private final AuthService authService;

    public EmployeeService(EmployeeRepository employeeRepository,
                           UserAccountRepository userRepository,
                           SupervisorRepository supervisorRepository,
                           WorkScheduleRepository scheduleRepository,
                           WorkLocationRepository locationRepository,
                           DeviceCredentialRepository credentialRepository,
                           ActivationTokenRepository activationTokenRepository,
                           WorkEventRepository workEventRepository,
                           LocationService locationService,
                           AuditService auditService,
                           AuthService authService) {
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.supervisorRepository = supervisorRepository;
        this.scheduleRepository = scheduleRepository;
        this.locationRepository = locationRepository;
        this.credentialRepository = credentialRepository;
        this.activationTokenRepository = activationTokenRepository;
        this.workEventRepository = workEventRepository;
        this.locationService = locationService;
        this.auditService = auditService;
        this.authService = authService;
    }

    // -------------------------------------------------------------------------
    // Reading
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<EmployeeRow> list(Branch branch, WorkCategory category, AccountStatus status, UUID supervisorId) {
        List<Employee> employees = employeeRepository.search(branch, category, status, supervisorId);
        return decorate(employees);
    }

    /** Adds device status and latest recorded location to each row, in two queries rather than N. */
    @Transactional(readOnly = true)
    public List<EmployeeRow> decorate(List<Employee> employees) {
        if (employees.isEmpty()) {
            return List.of();
        }

        List<UUID> ids = employees.stream().map(Employee::getId).toList();

        Map<UUID, DeviceCredential> credentials = new HashMap<>();
        for (UUID id : ids) {
            credentialRepository.findFirstByEmployeeIdAndStatus(id, CredentialStatus.ACTIVE)
                    .ifPresent(credential -> credentials.put(id, credential));
        }

        Map<UUID, WorkEvent> latest = new HashMap<>();
        for (WorkEvent event : workEventRepository.findLatestForEmployees(ids)) {
            latest.merge(event.getEmployee().getId(), event,
                    (a, b) -> a.getEventTime().isAfter(b.getEventTime()) ? a : b);
        }

        List<EmployeeRow> rows = new ArrayList<>(employees.size());
        for (Employee employee : employees) {
            DeviceCredential credential = credentials.get(employee.getId());
            WorkEvent event = latest.get(employee.getId());
            rows.add(new EmployeeRow(
                    employee.getId(),
                    employee.getEmployeeNo(),
                    employee.fullName(),
                    employee.getEmail(),
                    employee.getContactNumber(),
                    employee.getBranch(),
                    employee.getWorkCategory(),
                    employee.getSupervisor() == null ? null : employee.getSupervisor().fullName(),
                    employee.getSupervisor() == null ? null : employee.getSupervisor().getId(),
                    employee.getWorkSchedule() == null ? null : employee.getWorkSchedule().getName(),
                    employee.getWorkSchedule() == null ? null : employee.getWorkSchedule().getId(),
                    employee.getAccountStatus(),
                    credential == null ? null : credential.getStatus(),
                    credential == null ? null : credential.getDeviceLabel(),
                    event == null || event.getLocation() == null ? null : event.getLocation().getName(),
                    event == null ? null : event.getEventTime(),
                    event == null ? null : event.getEventType(),
                    event == null ? null : Labels.eventLabel(event.getEventType())));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public EmployeeDetail detail(UUID employeeId) {
        Employee employee = require(employeeId);
        EmployeeRow row = decorate(List.of(employee)).get(0);

        DeviceCredential credential = credentialRepository
                .findByEmployeeIdOrderByCreatedAtDesc(employeeId).stream()
                .findFirst()
                .orElse(null);

        ActivationToken pending = activationTokenRepository
                .findByUserIdOrderByCreatedAtDesc(employee.getUser().getId()).stream()
                .filter(ActivationToken::isUsable)
                .findFirst()
                .orElse(null);

        return new EmployeeDetail(
                row,
                employee.getHomeOffice() == null ? null : employee.getHomeOffice().getName(),
                employee.getHiredOn(),
                employee.getCreatedAt(),
                employee.getUser().getLastLoginAt(),
                credential == null ? null : new DeviceSecurityView(
                        credential.getId(),
                        credential.getDeviceLabel(),
                        credential.getStatus(),
                        credential.getAlgorithm(),
                        credential.getCreatedAt(),
                        credential.getLastUsedAt(),
                        credential.getRevokedAt(),
                        credential.getRevokedReason()),
                pending != null,
                pending == null ? null : pending.getExpiresAt());
    }

    @Transactional(readOnly = true)
    public Employee require(UUID id) {
        return employeeRepository.findDetailedById(id)
                .orElseThrow(() -> ApiException.notFound("That employee could not be found."));
    }

    // -------------------------------------------------------------------------
    // Writing
    // -------------------------------------------------------------------------

    /**
     * Creates an employee and their login. The account starts as
     * PENDING_ACTIVATION with no registered device - the employee sets both up
     * themselves from the invitation link.
     */
    @Transactional
    public Employee create(AuthPrincipal actor, CreateEmployeeRequest request) {
        String email = request.email().trim().toLowerCase();

        if (employeeRepository.existsByEmployeeNoIgnoreCase(request.employeeNo())) {
            throw ApiException.conflict("EMPLOYEE_NO_TAKEN", "That Employee ID is already in use.");
        }
        if (userRepository.existsByEmailIgnoreCase(email) || employeeRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("EMAIL_TAKEN", "That email address is already in use.");
        }

        UserAccount user = new UserAccount();
        user.setEmail(email);
        user.setRole(Role.EMPLOYEE);
        user.setStatus(AccountStatus.PENDING_ACTIVATION);
        user.setDisplayName(request.firstName().trim() + " " + request.lastName().trim());
        userRepository.save(user);

        Employee employee = new Employee();
        employee.setEmployeeNo(request.employeeNo().trim().toUpperCase());
        employee.setUser(user);
        employee.setFirstName(request.firstName().trim());
        employee.setLastName(request.lastName().trim());
        employee.setEmail(email);
        employee.setContactNumber(blankToNull(request.contactNumber()));
        employee.setBranch(request.branch());
        employee.setWorkCategory(request.workCategory());
        employee.setAccountStatus(AccountStatus.PENDING_ACTIVATION);
        employee.setHiredOn(request.hiredOn());

        applySupervisor(employee, request.supervisorId());
        applySchedule(employee, request.workScheduleId());
        applyHomeOffice(employee, request.homeOfficeId(), request.branch());

        Employee saved = employeeRepository.save(employee);

        auditService.success(actor, "CREATE_EMPLOYEE", "Employee", saved.getId(), saved.getEmployeeNo(),
                "Created " + saved.fullName() + " (" + saved.getBranch() + ", " + saved.getWorkCategory() + ")");

        return saved;
    }

    @Transactional
    public Employee update(AuthPrincipal actor, UUID employeeId, UpdateEmployeeRequest request) {
        Employee employee = require(employeeId);

        if (request.firstName() != null && !request.firstName().isBlank()) {
            employee.setFirstName(request.firstName().trim());
        }
        if (request.lastName() != null && !request.lastName().isBlank()) {
            employee.setLastName(request.lastName().trim());
        }
        if (request.email() != null && !request.email().isBlank()) {
            String email = request.email().trim().toLowerCase();
            if (!email.equals(employee.getEmail())
                    && (userRepository.existsByEmailIgnoreCase(email) || employeeRepository.existsByEmailIgnoreCase(email))) {
                throw ApiException.conflict("EMAIL_TAKEN", "That email address is already in use.");
            }
            employee.setEmail(email);
            employee.getUser().setEmail(email);
        }
        if (request.contactNumber() != null) {
            employee.setContactNumber(blankToNull(request.contactNumber()));
        }
        if (request.branch() != null) {
            employee.setBranch(request.branch());
        }
        if (request.workCategory() != null) {
            // Work category describes how someone is employed, not where they
            // are today, so this only changes when an administrator says so.
            employee.setWorkCategory(request.workCategory());
        }
        if (request.hiredOn() != null) {
            employee.setHiredOn(request.hiredOn());
        }

        applySupervisor(employee, request.supervisorId());
        applySchedule(employee, request.workScheduleId());
        applyHomeOffice(employee, request.homeOfficeId(), employee.getBranch());

        employee.getUser().setDisplayName(employee.fullName());
        userRepository.save(employee.getUser());
        Employee saved = employeeRepository.save(employee);

        auditService.success(actor, "UPDATE_EMPLOYEE", "Employee", saved.getId(), saved.getEmployeeNo(),
                "Updated " + saved.fullName());
        return saved;
    }

    @Transactional
    public Employee setEnabled(AuthPrincipal actor, UUID employeeId, boolean enabled, String reason) {
        Employee employee = require(employeeId);
        UserAccount user = employee.getUser();

        if (enabled) {
            // Re-enabling an employee who never activated returns them to
            // pending, not active - they still need to set a password.
            AccountStatus restored = user.getPasswordHash() == null
                    ? AccountStatus.PENDING_ACTIVATION
                    : AccountStatus.ACTIVE;
            user.setStatus(restored);
            employee.setAccountStatus(restored);
        } else {
            user.setStatus(AccountStatus.DISABLED);
            employee.setAccountStatus(AccountStatus.DISABLED);
            authService.revokeAllSessions(user);
        }

        userRepository.save(user);
        Employee saved = employeeRepository.save(employee);

        auditService.success(actor, enabled ? "ENABLE_EMPLOYEE" : "DISABLE_EMPLOYEE",
                "Employee", saved.getId(), saved.getEmployeeNo(),
                reason == null || reason.isBlank() ? null : "Reason: " + reason);
        return saved;
    }

    /**
     * Revokes the registered device. The employee account stays active and all
     * historical attendance is untouched - only the phone stops being able to
     * verify new activity.
     */
    @Transactional
    public void revokeDevice(AuthPrincipal actor, UUID employeeId, String reason) {
        Employee employee = require(employeeId);
        UserAccount actorAccount = userRepository.findById(actor.userId()).orElse(null);

        DeviceCredential credential = credentialRepository
                .findFirstByEmployeeIdAndStatus(employeeId, CredentialStatus.ACTIVE)
                .orElseThrow(() -> ApiException.badRequest("NO_ACTIVE_DEVICE",
                        "This employee has no registered device to revoke."));

        credential.setStatus(CredentialStatus.REVOKED);
        credential.setRevokedAt(Instant.now());
        credential.setRevokedBy(actorAccount);
        credential.setRevokedReason(blankToNull(reason));
        credentialRepository.save(credential);

        auditService.success(actor, "REVOKE_DEVICE_CREDENTIAL", "Employee", employee.getId(), employee.getEmployeeNo(),
                "Device revoked" + (reason == null || reason.isBlank() ? "" : ": " + reason));
    }

    @Transactional(readOnly = true)
    public UserAccount userFor(UUID employeeId) {
        return require(employeeId).getUser();
    }

    // -------------------------------------------------------------------------
    // Field helpers
    // -------------------------------------------------------------------------

    private void applySupervisor(Employee employee, UUID supervisorId) {
        if (supervisorId == null) {
            return;
        }
        Supervisor supervisor = supervisorRepository.findById(supervisorId)
                .orElseThrow(() -> ApiException.badRequest("SUPERVISOR_NOT_FOUND", "That supervisor could not be found."));
        employee.setSupervisor(supervisor);
    }

    private void applySchedule(Employee employee, UUID scheduleId) {
        if (scheduleId == null) {
            return;
        }
        WorkSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> ApiException.badRequest("SCHEDULE_NOT_FOUND", "That work schedule could not be found."));
        employee.setWorkSchedule(schedule);
    }

    private void applyHomeOffice(Employee employee, UUID officeId, Branch branch) {
        if (officeId != null) {
            employee.setHomeOffice(locationRepository.findById(officeId)
                    .orElseThrow(() -> ApiException.badRequest("LOCATION_NOT_FOUND", "That office could not be found.")));
            return;
        }
        if (employee.getHomeOffice() == null && branch != null) {
            locationService.defaultOfficeFor(branch).ifPresent(employee::setHomeOffice);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
