package com.uniace.trackr.security;

import com.uniace.trackr.domain.enums.Role;
import java.util.UUID;

/**
 * The authenticated caller, resolved from the access token on every request.
 *
 * <p>{@code employeeId} and {@code supervisorId} are carried here so that scope
 * checks never have to trust anything the client sent.
 */
public record AuthPrincipal(
        UUID userId,
        String email,
        String displayName,
        Role role,
        UUID employeeId,
        UUID supervisorId
) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public boolean isSupervisor() {
        return role == Role.SUPERVISOR;
    }

    public boolean isEmployee() {
        return role == Role.EMPLOYEE;
    }
}
