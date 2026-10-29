package com.uniace.trackr.security;

import com.uniace.trackr.config.TrackrProperties;
import com.uniace.trackr.domain.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Issues and verifies stateless access tokens. */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final String ISSUER = "trackr-uni-ace";

    private final TrackrProperties properties;
    private SecretKey key;

    public JwtService(TrackrProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void init() {
        String secret = properties.getSecurity().getJwtSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET must be set to at least 32 characters. Generate one with: openssl rand -base64 48");
        }
        if (secret.startsWith("dev-only-secret")) {
            log.warn("Using the built-in development JWT secret. Set JWT_SECRET before deploying.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public Duration accessTokenLifetime() {
        return Duration.ofMinutes(properties.getSecurity().getAccessTokenMinutes());
    }

    public String issueAccessToken(AuthPrincipal principal) {
        Instant now = Instant.now();
        Instant expiry = now.plus(accessTokenLifetime());
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(principal.userId().toString())
                .claim("email", principal.email())
                .claim("name", principal.displayName())
                .claim("role", principal.role().name())
                .claim("empId", principal.employeeId() == null ? null : principal.employeeId().toString())
                .claim("supId", principal.supervisorId() == null ? null : principal.supervisorId().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    public Optional<AuthPrincipal> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(ISSUER)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return Optional.of(new AuthPrincipal(
                    UUID.fromString(claims.getSubject()),
                    claims.get("email", String.class),
                    claims.get("name", String.class),
                    Role.valueOf(claims.get("role", String.class)),
                    uuidClaim(claims, "empId"),
                    uuidClaim(claims, "supId")
            ));
        } catch (JwtException | IllegalArgumentException ex) {
            // Invalid, expired or tampered token - the request stays anonymous.
            return Optional.empty();
        }
    }

    private static UUID uuidClaim(Claims claims, String name) {
        String raw = claims.get(name, String.class);
        return (raw == null || raw.isBlank()) ? null : UUID.fromString(raw);
    }
}
