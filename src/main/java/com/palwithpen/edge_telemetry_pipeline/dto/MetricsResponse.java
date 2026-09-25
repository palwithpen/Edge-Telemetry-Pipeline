package com.palwithpen.edge_telemetry_pipeline.dto;

import java.time.Instant;

import lombok.Getter;
import lombok.ToString;

// Takes plain values in its constructor, not a WindowAccumulator directly — that's a
// deliberate boundary: dto stays unaware of the aggregation package's internal types.
// Whoever builds this (ReadingSvc) calls accumulator.average()/.p95()/.count() itself and
// hands over the results, same way DeviceResponse/ReadingResponse never leak entities.
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
