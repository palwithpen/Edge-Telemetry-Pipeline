package com.palwithpen.edge_telemetry_pipeline.dto;

import java.time.Instant;

import com.palwithpen.edge_telemetry_pipeline.model.ReadingEntity;

import lombok.Getter;

@Getter 
public class ReadingResponse {
    
    private final Long id;
    private final String deviceId;
    private final String metricType;
    private final Double value;
    private final Instant recordedAt;
    private final Instant ingestedAt;

    public ReadingResponse(ReadingEntity entity){
        this.id = entity.getId();
        this.deviceId = entity.getDevice().getId();
        this.metricType = entity.getMetricType();
        this.value = entity.getValue();
        this.recordedAt = entity.getRecordedAt();
        this.ingestedAt = entity.getIngestedAt();
    }
}
