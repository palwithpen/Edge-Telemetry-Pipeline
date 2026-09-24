package com.palwithpen.edge_telemetry_pipeline.worker;

import com.palwithpen.edge_telemetry_pipeline.dto.ReadingRequest;

public record ParsedReading(String deviceId, ReadingRequest request) {
    
}
