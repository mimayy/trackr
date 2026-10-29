package com.uniace.trackr.service;

import com.uniace.trackr.domain.Employee;
import com.uniace.trackr.domain.Supervisor;
import com.uniace.trackr.domain.UserAccount;
import com.uniace.trackr.domain.enums.AccountStatus;
import com.uniace.trackr.domain.enums.Role;
import com.uniace.trackr.dto.AdminDtos.CreateSupervisorRequest;
import com.uniace.trackr.dto.AdminDtos.SupervisorRow;
import com.uniace.trackr.dto.AdminDtos.UpdateSupervisorRequest;
import com.uniace.trackr.repo.EmployeeRepository;
import com.uniace.trackr.repo.SupervisorRepository;
import com.uniace.trackr.repo.UserAccountRepository;
import com.uniace.trackr.security.AuthPrincipal;
import com.uniace.trackr.web.error.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Supervisor accounts and their scope.
 *
 * <p>A supervisor's scope is simply the set of employees assigned to them.
 * Changing that assignment is how an administrator changes what a supervisor
 * can see, and it takes effect immediately on the server.
 */
@Service
public class SupervisorService {

    private final SupervisorRepository supervisorRepository;
    private final UserAccountRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final AuditService auditService;
    private final AuthService authService;

    public SupervisorService(SupervisorRepository supervisorRepository,
                             UserAccountRepository userRepository,
                             EmployeeRepository employeeRepository,
                             AuditService auditService,
                             AuthService authService) {
        this.supervisorRepository = supervisorRepository;
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.auditService = auditService;
        this.authService = authService;
    }

    @Transactional(readOnly = true)
    public List<SupervisorRow> list() {
        return supervisorRepository.findAllByOrderByLastNameAscFirstNameAsc().stream()
                .map(this::toRow)
                .toList();
    }

    @Transactional(readOnly = true)
    public Supervisor require(UUID id) {
        return supervisorRepository.findDetailedById(id)
                .orElseThrow(() -> ApiException.notFound("That supervisor could not be found."));
    }

    @Transactional
    public Supervisor create(AuthPrincipal actor, CreateSupervisorRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email) || supervisorRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("EMAIL_TAKEN", "That email address is already in use.");
        }

        UserAccount user = new UserAccount();
        user.setEmail(email);
        user.setRole(Role.SUPERVISOR);
        user.setStatus(AccountStatus.PENDING_ACTIVATION);
        user.setDisplayName(request.firstName().trim() + " " + request.lastName().trim());
        userRepository.save(user);

        Supervisor supervisor = new Supervisor();
        supervisor.setUser(user);
        supervisor.setFirstName(request.firstName().trim());
        supervisor.setLastName(request.lastName().trim());
        supervisor.setEmail(email);
        supervisor.setContactNumber(blankToNull(request.contactNumber()));
        supervisor.setBranch(request.branch());
        supervisor.setScopeNote(blankToNull(request.scopeNote()));

        Supervisor saved = supervisorRepository.save(supervisor);
        auditService.success(actor, "CREATE_SUPERVISOR", "Supervisor", saved.getId(), saved.fullName(),
                "Created supervisor for " + saved.getBranch());
        return saved;
    }

    @Transactional
    public Supervisor update(AuthPrincipal actor, UUID id, UpdateSupervisorRequest request) {
        Supervisor supervisor = require(id);

        if (request.firstName() != null && !request.firstName().isBlank()) {
            supervisor.setFirstName(request.firstName().trim());
        }
        if (request.lastName() != null && !request.lastName().isBlank()) {
            supervisor.setLastName(request.lastName().trim());
        }
        if (request.email() != null && !request.email().isBlank()) {
            String email = request.email().trim().toLowerCase();
            if (!email.equals(supervisor.getEmail())
                    && (userRepository.existsByEmailIgnoreCase(email) || supervisorRepository.existsByEmailIgnoreCase(email))) {
                throw ApiException.conflict("EMAIL_TAKEN", "That email address is already in use.");
            }
            supervisor.setEmail(email);
            supervisor.getUser().setEmail(email);
        }
        if (request.contactNumber() != null) {
            supervisor.setContactNumber(blankToNull(request.contactNumber()));
        }
        if (request.branch() != null) {
            supervisor.setBranch(request.branch());
        }
        if (request.scopeNote() != null) {
            supervisor.setScopeNote(blankToNull(request.scopeNote()));
        }

        supervisor.getUser().setDisplayName(supervisor.fullName());
        userRepository.save(supervisor.getUser());
        Supervisor saved = supervisorRepository.save(supervisor);

        auditService.success(actor, "UPDATE_SUPERVISOR", "Supervisor", saved.getId(), saved.fullName(), null);
        return saved;
    }

    @Transactional
    public Supervisor setEnabled(AuthPrincipal actor, UUID id, boolean enabled) {
        Supervisor supervisor = require(id);
        UserAccount user = supervisor.getUser();

        if (enabled) {
            user.setStatus(user.getPasswordHash() == null
                    ? AccountStatus.PENDING_ACTIVATION
                    : AccountStatus.ACTIVE);
        } else {
            user.setStatus(AccountStatus.DISABLED);
            authService.revokeAllSessions(user);
        }
        supervisor.setActive(enabled);

        userRepository.save(user);
        Supervisor saved = supervisorRepository.save(supervisor);

        auditService.success(actor, enabled ? "ENABLE_SUPERVISOR" : "DISABLE_SUPERVISOR",
                "Supervisor", saved.getId(), saved.fullName(), null);
        return saved;
    }

    /** Replaces the set of employees this supervisor is responsible for. */
    @Transactional
    public int assignEmployees(AuthPrincipal actor, UUID supervisorId, List<UUID> employeeIds) {
        Supervisor supervisor = require(supervisorId);

        List<Employee> current = employeeRepository.findBySupervisorId(supervisorId);
        current.stream()
                .filter(employee -> !employeeIds.contains(employee.getId()))
                .forEach(employee -> employee.setSupervisor(null));
        employeeRepository.saveAll(current);

        List<Employee> target = employeeRepository.findAllById(employeeIds);
        if (target.size() != employeeIds.size()) {
            throw ApiException.badRequest("EMPLOYEE_NOT_FOUND", "One of the selected employees could not be found.");
        }
        target.forEach(employee -> employee.setSupervisor(supervisor));
        employeeRepository.saveAll(target);

        auditService.success(actor, "ASSIGN_EMPLOYEES_TO_SUPERVISOR", "Supervisor",
                supervisorId, supervisor.fullName(),
                target.size() + " employees now report to " + supervisor.fullName());

        return target.size();
    }

    @Transactional(readOnly = true)
    public SupervisorRow toRow(Supervisor supervisor) {
        return new SupervisorRow(
                supervisor.getId(),
                supervisor.fullName(),
                supervisor.getEmail(),
                supervisor.getContactNumber(),
                supervisor.getBranch(),
                supervisor.getScopeNote(),
                supervisor.getUser().getStatus(),
                employeeRepository.findBySupervisorId(supervisor.getId()).size(),
                supervisor.isActive());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
