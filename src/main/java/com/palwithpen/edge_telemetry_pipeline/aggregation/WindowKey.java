package com.palwithpen.edge_telemetry_pipeline.aggregation;

import java.time.Instant;

// Identifies one tumbling 1-minute bucket for one device+metric. windowStart is derived from
// the reading's recordedAt (event time), not from when we happened to receive it — that
// choice is what makes "late data" a meaningful concept at all in MetricWindowAggregator.
public record WindowKey(String deviceId, String metricType, Instant windowStart) {

}
