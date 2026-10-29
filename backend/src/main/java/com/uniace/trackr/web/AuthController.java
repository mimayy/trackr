package com.uniace.trackr.web;

import com.uniace.trackr.dto.AuthDtos.LoginRequest;
import com.uniace.trackr.dto.AuthDtos.RefreshRequest;
import com.uniace.trackr.dto.AuthDtos.SessionResponse;
import com.uniace.trackr.dto.AuthDtos.SessionUser;
import com.uniace.trackr.security.AuthPrincipal;
import com.uniace.trackr.service.AuthService;
import com.uniace.trackr.service.ScopeService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final ScopeService scopeService;

    public AuthController(AuthService authService, ScopeService scopeService) {
        this.authService = authService;
        this.scopeService = scopeService;
    }

    @PostMapping("/login")
    public SessionResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public SessionResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(@RequestBody(required = false) RefreshRequest request) {
        if (request != null) {
            authService.logout(request.refreshToken());
        }
        return ResponseEntity.ok(Map.of("message", "You have been signed out."));
    }

    /** Who am I? Used on page load to restore the right interface. */
    @GetMapping("/session")
    public SessionUser session(@AuthenticationPrincipal AuthPrincipal principal) {
        return authService.sessionUser(scopeService.requireUser(principal));
    }
}
