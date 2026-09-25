package com.palwithpen.edge_telemetry_pipeline.dto;

import java.time.Instant;

import lombok.Getter;

// The one response shape every endpoint returns, success or failure (see
// GlobalExceptionHandler for the failure side) — a client always gets the same envelope
// regardless of which endpoint or outcome it hit. Generic on T so `data` can be a
// DeviceResponse, a List<Map<String,String>> of validation errors, or nothing at all
// (Void, on a 204-ish delete) without a new wrapper class per shape.
@Getter
public class ApiResponse<T> {

    private final int status;
    private final T data;
    private final Instant timestamp;

    // No setters, no no-arg constructor — this is a response, built once and handed off,
    // never mutated afterward. timestamp is stamped here rather than passed in, so nobody
    // has to remember to supply Instant.now() correctly at every call site.
    public ApiResponse(int status, T data) {
        this.status = status;
        this.data = data;
        this.timestamp = Instant.now();
    }
}
