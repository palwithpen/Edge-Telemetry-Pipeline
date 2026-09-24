package com.palwithpen.edge_telemetry_pipeline.dto;

import java.time.Instant;

import lombok.Getter;

@Getter 
public class ApiResponse<T> {

    private final int status;
    private final T data;
    private final Instant timestamp;
    
    public ApiResponse(int status, T data) {
        this.status = status;
        this.data = data;
        this.timestamp = Instant.now();
    }
}
