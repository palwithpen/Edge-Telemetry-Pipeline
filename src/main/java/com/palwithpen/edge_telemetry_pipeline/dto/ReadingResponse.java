package com.palwithpen.edge_telemetry_pipeline.dto;

import java.time.Instant;

import com.palwithpen.edge_telemetry_pipeline.model.ReadingEntity;

import lombok.Getter;

// Both timestamps are exposed here, not just recordedAt — the gap between them is exactly
// what P4's late-data handling is about, so it's worth a client being able to see it too.
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
        // entity.getDevice() is a LAZY association — touching it here only works because
        // this constructor runs inside the same transaction/session that fetched or just
        // saved the entity. Building this from a detached entity later would throw.
        this.deviceId = entity.getDevice().getId();
        this.metricType = entity.getMetricType();
        this.value = entity.getValue();
        this.recordedAt = entity.getRecordedAt();
        this.ingestedAt = entity.getIngestedAt();
    }
}
