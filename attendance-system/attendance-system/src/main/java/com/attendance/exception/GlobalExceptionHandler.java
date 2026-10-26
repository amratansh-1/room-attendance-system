package com.attendance.exception;

import jakarta.persistence.PersistenceException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Turns every kind of failure into one consistent JSON error body. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** SQLSTATE used by our triggers: SIGNAL SQLSTATE '45000'. */
    private static final String TRIGGER_SQLSTATE = "45000";

    public record ErrorResponse(String timestamp, int status, String error, String message) {
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(Instant.now().toString(), status.value(),
                        status.getReasonPhrase(), message));
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex) {
        return build(ex.getStatus(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleBadInput(Exception ex) {
        return build(HttpStatus.BAD_REQUEST,
                "Invalid request: check the JSON, enum values (e.g. PRESENT/ABSENT/LATE) and dates (yyyy-MM-dd)");
    }

    /**
     * Database errors. If a trigger rejected the change (SQLSTATE 45000) the trigger's own
     * message is safe and useful, so it is returned. Anything else is logged, not exposed.
     */
    @ExceptionHandler({DataAccessException.class, PersistenceException.class, TransactionException.class})
    public ResponseEntity<ErrorResponse> handleDatabase(Exception ex) {
        SQLException sql = findSqlException(ex);
        if (sql != null && TRIGGER_SQLSTATE.equals(sql.getSQLState())) {
            return build(HttpStatus.CONFLICT, sql.getMessage());
        }
        if (ex instanceof DataIntegrityViolationException) {
            return build(HttpStatus.CONFLICT, "Duplicate value or reference to a record that does not exist");
        }
        log.error("Unexpected database error", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Database error");
    }

    private static SQLException findSqlException(Throwable t) {
        int guard = 0;
        while (t != null && guard++ < 20) {
            if (t instanceof SQLException sql) {
                return sql;
            }
            t = t.getCause();
        }
        return null;
    }
}
