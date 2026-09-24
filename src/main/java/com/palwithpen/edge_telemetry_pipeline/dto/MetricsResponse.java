package com.palwithpen.edge_telemetry_pipeline.dto;

import java.time.Instant;

import lombok.Getter;
import lombok.ToString;

@Getter  
@ToString 
public class MetricsResponse {
    
    private final String deviceId;
    private final String metricType;
    private final Instant windowStart;
    private final double average;
    private final double p95;
    private final int count;

    public MetricsResponse(String deviceId, String metricType, Instant windowStart,
                            double average, double p95, int count) {
        this.deviceId = deviceId;
        this.metricType = metricType;
        this.windowStart = windowStart;
        this.average = average;
        this.p95 = p95;
        this.count = count;
    }

}
