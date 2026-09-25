package com.palwithpen.edge_telemetry_pipeline.exception;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import com.palwithpen.edge_telemetry_pipeline.dto.ApiResponse;

// Every ResponseStatusException thrown anywhere in the app (DeviceSvc, ReadingSvc, etc.)
// and every failed @Valid gets routed here instead of Spring's default error page, so the
// client always gets the same ApiResponse envelope shape regardless of what went wrong.
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Reads the status/message straight off whatever was thrown (DEVICE_NOT_FOUND, 409 on a
    // duplicate id, etc.) rather than hardcoding anything here — this handler doesn't know
    // or care which specific business rule fired, it just relays it consistently.
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<String>> handleResponseStatusException(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(new ApiResponse<>(ex.getStatusCode().value(),ex.getReason()));
    }

    // A list, not a flat map, specifically so two different validation failures on the same
    // field (rare, but possible) don't overwrite each other — a plain Map<String,String>
    // keyed by field name would silently lose the second error via a duplicate key.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<List<Map<String,String>>>> handleValidationException(MethodArgumentNotValidException ex) {
        List<Map<String,String>> errors = ex.getBindingResult()
        .getFieldErrors()
        .stream()
        .map(fieldError -> Map.of(
            fieldError.getField(),fieldError.getDefaultMessage()
        )).toList();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(HttpStatus.BAD_REQUEST.value(), errors));
    }

}
