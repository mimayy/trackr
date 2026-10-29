package com.uniace.trackr.service;

import com.uniace.trackr.domain.Employee;
import com.uniace.trackr.domain.Supervisor;
import com.uniace.trackr.domain.UserAccount;
import com.uniace.trackr.repo.EmployeeRepository;
import com.uniace.trackr.repo.SupervisorRepository;
import com.uniace.trackr.repo.UserAccountRepository;
import com.uniace.trackr.security.AuthPrincipal;
import com.uniace.trackr.web.error.ApiException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single place that answers "whose data may this caller see?".
 *
 * <p>Every supervisor and administrator endpoint routes its authorisation
 * through here, so scope is decided once, on the server, from the token - never
 * from an id the client supplied.
 */
@Service
public class ScopeService {

    /**
     * Used where a query filters with {@code IN (:ids)} and the caller's scope is
     * empty. An impossible id keeps the SQL valid and returns nothing, which is
     * the correct answer for a supervisor with no employees yet.
     */
    private static final UUID MATCHES_NOTHING = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final EmployeeRepository employeeRepository;
    private final SupervisorRepository supervisorRepository;
    private final UserAccountRepository userAccountRepository;

    public ScopeService(EmployeeRepository employeeRepository,
                        SupervisorRepository supervisorRepository,
                        UserAccountRepository userAccountRepository) {
        this.employeeRepository = employeeRepository;
        this.supervisorRepository = supervisorRepository;
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional(readOnly = true)
    public UserAccount requireUser(AuthPrincipal principal) {
        return userAccountRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.unauthorized(
                        "We could not verify your session. Please log in again."));
    }

    /** The employee profile behind the current login, for the {@code /api/me} endpoints. */
    @Transactional(readOnly = true)
    public Employee requireEmployee(AuthPrincipal principal) {
        return employeeRepository.findDetailedByUserId(principal.userId())
                .orElseThrow(() -> ApiException.forbidden(
                        "This account is not set up as an employee, so it has no work activity to show."));
    }

    @Transactional(readOnly = true)
    public Supervisor requireSupervisor(AuthPrincipal principal) {
        return supervisorRepository.findByUserId(principal.userId())
                .orElseThrow(() -> ApiException.forbidden(
                        "This account is not set up as a supervisor."));
    }

    /** True when the caller's view must be narrowed to a subset of employees. */
    public boolean isScoped(AuthPrincipal principal) {
        return !principal.isAdmin();
    }

    /**
     * The employees this caller may see. Administrators see everyone;
     * supervisors see the employees assigned to them; an employee sees only
     * themselves.
     */
    @Transactional(readOnly = true)
    public List<UUID> visibleEmployeeIds(AuthPrincipal principal) {
        if (principal.isAdmin()) {
            List<UUID> all = new ArrayList<>(employeeRepository.findAll().stream().map(Employee::getId).toList());
            return all.isEmpty() ? List.of(MATCHES_NOTHING) : all;
        }

        if (principal.isSupervisor()) {
            Supervisor supervisor = requireSupervisor(principal);
            List<UUID> ids = new ArrayList<>(employeeRepository.findBySupervisorId(supervisor.getId())
                    .stream().map(Employee::getId).toList());
            return ids.isEmpty() ? List.of(MATCHES_NOTHING) : ids;
        }

        return List.of(requireEmployee(principal).getId());
    }

    /** The employees in scope, as full records, for list screens. */
    @Transactional(readOnly = true)
    public List<Employee> visibleEmployees(AuthPrincipal principal) {
        if (principal.isAdmin()) {
            return employeeRepository.search(null, null, null, null);
        }
        if (principal.isSupervisor()) {
            Supervisor supervisor = requireSupervisor(principal);
            return employeeRepository.search(null, null, null, supervisor.getId());
        }
        return List.of(requireEmployee(principal));
    }

    /**
     * Loads an employee, refusing when the caller is not allowed to see them.
     *
     * <p>The message is deliberately the same whether the employee does not
     * exist or is simply out of scope, so it cannot be used to probe for who
     * works here.
     */
    @Transactional(readOnly = true)
    public Employee requireVisibleEmployee(AuthPrincipal principal, UUID employeeId) {
        Employee employee = employeeRepository.findDetailedById(employeeId)
                .orElseThrow(() -> ApiException.forbidden("You do not have access to this employee's information."));

        if (principal.isAdmin()) {
            return employee;
        }
        if (principal.isSupervisor()) {
            Supervisor supervisor = requireSupervisor(principal);
            if (employee.getSupervisor() != null && employee.getSupervisor().getId().equals(supervisor.getId())) {
                return employee;
            }
            throw ApiException.forbidden("You do not have access to this employee's information.");
        }
        if (employee.getUser().getId().equals(principal.userId())) {
            return employee;
        }
        throw ApiException.forbidden("You do not have access to this employee's information.");
    }

    /** The supervisor id a supervisor's queries must be filtered by; null for administrators. */
    @Transactional(readOnly = true)
    public UUID supervisorFilter(AuthPrincipal principal) {
        return principal.isAdmin() ? null : requireSupervisor(principal).getId();
    }
}
