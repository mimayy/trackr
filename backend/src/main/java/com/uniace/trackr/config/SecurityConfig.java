package com.uniace.trackr.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniace.trackr.security.AuthRateLimitFilter;
import com.uniace.trackr.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Role-based access control, enforced on the server.
 *
 * <p>The URL rules below are the outer boundary; individual endpoints add scope
 * checks on top (a supervisor may call {@code /api/supervisor/**} but still only
 * sees the employees assigned to them). Hiding buttons in the UI is never the
 * security mechanism.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final TrackrProperties properties;

    public SecurityConfig(TrackrProperties properties) {
        this.properties = properties;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthenticationFilter jwtFilter,
                                           AuthRateLimitFilter rateLimitFilter,
                                           ObjectMapper objectMapper) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // The API is stateless and token-authenticated, so there is no
                // session cookie for CSRF to protect.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(opts -> {})
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/actuator/health", "/actuator/health/**", "/actuator/info",
                                "/api/auth/login", "/api/auth/refresh",
                                "/api/activation/**",
                                "/api/config/public",
                                "/error").permitAll()
                        // Employees only ever reach their own data.
                        .requestMatchers("/api/me/**").hasAnyRole("EMPLOYEE", "SUPERVISOR", "ADMIN")
                        .requestMatchers("/api/supervisor/**").hasAnyRole("SUPERVISOR", "ADMIN")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                writeError(response, objectMapper, HttpServletResponse.SC_UNAUTHORIZED,
                                        "UNAUTHENTICATED",
                                        "We could not verify your session. Please log in again."))
                        .accessDeniedHandler((request, response, deniedException) ->
                                writeError(response, objectMapper, HttpServletResponse.SC_FORBIDDEN,
                                        "FORBIDDEN",
                                        "You do not have access to this information.")))
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private static void writeError(HttpServletResponse response,
                                   ObjectMapper mapper,
                                   int status,
                                   String code,
                                   String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), Map.of("code", code, "message", message));
    }

    /**
     * Allowed origins come from FRONTEND_URL. Nothing about localhost is baked
     * into the build, so the same artifact serves the Vercel front end.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "X-Client-Version"));
        config.setExposedHeaders(List.of("Content-Disposition"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
