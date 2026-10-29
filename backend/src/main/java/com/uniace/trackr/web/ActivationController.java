package com.uniace.trackr.web;

import com.uniace.trackr.dto.AuthDtos.ActivateRequest;
import com.uniace.trackr.dto.AuthDtos.ActivationDetails;
import com.uniace.trackr.dto.AuthDtos.SessionResponse;
import com.uniace.trackr.service.ActivationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

/**
 * Account activation. Unauthenticated by design - the token in the link is what
 * authorises the call, and it is single use, short lived and stored only as a
 * hash. The rate limiter covers these paths.
 */
@RestController
@RequestMapping("/api/activation")
public class ActivationController {

    private final ActivationService activationService;

    public ActivationController(ActivationService activationService) {
        this.activationService = activationService;
    }

    /** Shows the employee their own name and details before they set a password. */
    @GetMapping("/details")
    public ActivationDetails details(@RequestParam("token") @NotBlank String token) {
        return activationService.describe(token);
    }

    /** Sets the password, activates the account and signs the employee in. */
    @PostMapping("/complete")
    public SessionResponse complete(@Valid @RequestBody ActivateRequest request) {
        return activationService.activate(request.token(), request.password());
    }
}
