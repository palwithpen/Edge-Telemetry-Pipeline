package com.palwithpen.edge_telemetry_pipeline.aggregation;

import java.time.Instant;

public record WindowKey(String deviceId, String metricType, Instant windowStart) {
    
}
