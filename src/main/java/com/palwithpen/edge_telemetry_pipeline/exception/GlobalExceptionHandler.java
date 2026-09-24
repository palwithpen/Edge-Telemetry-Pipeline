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

@RestControllerAdvice 
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<String>> handleResponseStatusException(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(new ApiResponse<>(ex.getStatusCode().value(),ex.getReason()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<List<Map<String,String>>>> handleValidationException(MethodArgumentNotValidException ex) {
        // ex.getBindingResult().getFieldErrors() gives you a List<FieldError>
        // each FieldError has .getField() and .getDefaultMessage()
        // build a List<Map<String,String>> from those, wrap in ApiResponse, return 400
        List<Map<String,String>> errors = ex.getBindingResult()
        .getFieldErrors()
        .stream()
        .map(fieldError -> Map.of(
            fieldError.getField(),fieldError.getDefaultMessage()
        )).toList();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(HttpStatus.BAD_REQUEST.value(), errors));
    }

}
