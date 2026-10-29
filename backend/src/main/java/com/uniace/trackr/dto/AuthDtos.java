package com.uniace.trackr.dto;

import com.uniace.trackr.domain.enums.AccountStatus;
import com.uniace.trackr.domain.enums.Branch;
import com.uniace.trackr.domain.enums.Role;
import com.uniace.trackr.domain.enums.WorkCategory;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "Please enter your email") @Email(message = "Please check your email address") String email,
            @NotBlank(message = "Please enter your password") String password
    ) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    /** Everything the front end needs to render the right interface for this person. */
    public record SessionUser(
            UUID userId,
            String email,
            String displayName,
            String firstName,
            Role role,
            AccountStatus accountStatus,
            UUID employeeId,
            String employeeNo,
            Branch branch,
            WorkCategory workCategory,
            String supervisorName,
            String scheduleName,
            String homeOfficeName,
            boolean deviceRegistered,
            String deviceLabel
    ) {
    }

    public record SessionResponse(
            String accessToken,
            String refreshToken,
            long expiresInSeconds,
            SessionUser user
    ) {
    }

    /** Shown on the activation screen before the employee sets a password. */
    public record ActivationDetails(
            String firstName,
            String fullName,
            String employeeNo,
            String email,
            Branch branch,
            WorkCategory workCategory,
            String supervisorName,
            String scheduleName,
            Instant expiresAt
    ) {
    }

    public record ActivateRequest(
            @NotBlank String token,
            @NotBlank(message = "Please choose a password")
            @Size(min = 10, max = 100, message = "Please use at least 10 characters")
            String password
    ) {
    }

    /** The activation link an administrator copies or emails. */
    public record InvitationResponse(
            String activationUrl,
            Instant expiresAt,
            boolean emailSent,
            String emailNote
    ) {
    }
}
