package com.uniace.trackr.web.error;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Turns every failure into the same friendly JSON shape.
 *
 * <p>Stack traces and internal messages never reach the client; unexpected
 * failures are logged server side and reported as a plain, calm sentence.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApi(ApiException ex, HttpServletRequest request) {
        if (ex.getStatus().is5xxServerError()) {
            log.error("API error at {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        } else {
            log.debug("API error at {}: {} - {}", request.getRequestURI(), ex.getCode(), ex.getMessage());
        }
        Map<String, Object> body = base(ex.getCode(), ex.getMessage());
        if (!ex.getDetails().isEmpty()) {
            body.put("details", ex.getDetails());
        }
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new TreeMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));

        Map<String, Object> body = base("VALIDATION_FAILED", "Please check the highlighted fields and try again.");
        body.put("fields", fields);
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * A malformed body or an unusable parameter - including a value that is not
     * one of an enum's constants, which Jackson reports as unreadable input.
     * Without this it reaches the catch-all and is reported as a server fault.
     */
    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequest(Exception ex) {
        log.debug("Malformed request: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(base("BAD_REQUEST", "That request was not something we could process. Please try again."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(DataIntegrityViolationException ex) {
        log.warn("Database constraint rejected a write: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(base("CONFLICT", "That record already exists or conflicts with existing information."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(base("FORBIDDEN", "You do not have access to this information."));
    }

    /**
     * A URL that does not exist is the caller's mistake, not a server fault.
     *
     * <p>Without this it falls through to the catch-all below, which answers
     * 500 and writes a full stack trace to the log - noise that hides real
     * failures and tells the caller the wrong thing about what went wrong.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NoResourceFoundException ex, HttpServletRequest request) {
        log.debug("No handler for {} {}", request.getMethod(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(base("NOT_FOUND", "We could not find what you were looking for."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled error at {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(base("SERVER_ERROR", "Something went wrong on our side. Please try again in a moment."));
    }

    private static Map<String, Object> base(String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        body.put("timestamp", Instant.now().toString());
        return body;
    }
}
