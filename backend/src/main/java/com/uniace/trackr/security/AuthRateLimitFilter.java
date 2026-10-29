package com.uniace.trackr.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniace.trackr.config.TrackrProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * A small fixed-window rate limiter for the endpoints worth brute forcing:
 * login, activation and WebAuthn challenges.
 *
 * <p>State is per instance, which is the right trade-off for a single free-tier
 * backend. A multi-instance deployment would move this to a shared store.
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final TrackrProperties properties;
    private final ObjectMapper objectMapper;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public AuthRateLimitFilter(TrackrProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.startsWith("/api/auth/") || path.startsWith("/api/activation/") || path.startsWith("/api/webauthn/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String clientKey = clientKey(request);
        int limit = properties.getSecurity().getAuthRateLimitPerMinute();

        Window window = windows.compute(clientKey, (key, existing) -> {
            Instant now = Instant.now();
            if (existing == null || existing.startedAt.plus(WINDOW).isBefore(now)) {
                return new Window(now);
            }
            return existing;
        });

        if (window.count.incrementAndGet() > limit) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), Map.of(
                    "code", "TOO_MANY_REQUESTS",
                    "message", "Too many attempts. Please wait a minute and try again."
            ));
            return;
        }

        // Opportunistic cleanup so the map cannot grow without bound.
        if (windows.size() > 5_000) {
            Instant cutoff = Instant.now().minus(WINDOW);
            windows.entrySet().removeIf(entry -> entry.getValue().startedAt.isBefore(cutoff));
        }

        chain.doFilter(request, response);
    }

    private static String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private record Window(Instant startedAt, AtomicInteger count) {
        Window(Instant startedAt) {
            this(startedAt, new AtomicInteger());
        }
    }
}
