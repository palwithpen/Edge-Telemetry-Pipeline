package com.palwithpen.edge_telemetry_pipeline.aggregation;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

// The P4 piece. Every persisted reading (HTTP or MQTT, doesn't matter) flows through
// record() below, bucketed into tumbling 1-minute windows and kept in memory only — no
// persistence, no survival across a restart. That's a deliberate scope call: the interesting
// part of this phase was the concurrent, event-time-windowed state itself, not durability of
// the aggregates (that's what the raw `reading` table in Postgres is for).
@Component
public class MetricWindowAggregator {

    private static final Logger logger = LoggerFactory.getLogger(MetricWindowAggregator.class);
    private final int windowMinutes;
    private final Duration gracePeriod;
    private final Counter lateAcceptedCounter;
    private final Counter lateDroppedCounter;

    public MetricWindowAggregator(
            @Value("${window.duration-minutes}") int windowMinutes,
            @Value("${window.grace-period-seconds}") long gracePeriodSeconds,
            MeterRegistry meterRegistry
        ) {
        this.windowMinutes = windowMinutes;
        this.gracePeriod = Duration.ofSeconds(gracePeriodSeconds);
        this.lateAcceptedCounter = Counter.builder("reading.late.accepted").register(meterRegistry);
        this.lateDroppedCounter = Counter.builder("reading.late.dropped").register(meterRegistry);

    }

    // ConcurrentHashMap because multiple worker threads call record() at once, potentially
    // for the same window key. computeIfAbsent below is what makes "get or create" atomic —
    // without it, two threads could both see "no accumulator yet," both create one, and one
    // reading would silently disappear into the accumulator that lost the race.
    private final ConcurrentMap<WindowKey, WindowAccumulator> windows = new ConcurrentHashMap<>();

    public void record(String deviceId, String metricType, Instant recordedAt, double value) {
        // Bucketed by recordedAt — event time, when the device says it happened — not by
        // when we're processing it right now. Bucketing by processing time would make
        // "late" a meaningless concept, since nothing could ever arrive after its own
        // window if the window is defined by arrival.
        Instant windowStart = recordedAt.truncatedTo(ChronoUnit.MINUTES);
        Instant windowEnd = windowStart.plus(windowMinutes, ChronoUnit.MINUTES);
        Instant now = Instant.now();

        if (windowEnd.isBefore(now)) {
            Duration lateBy = Duration.between(windowEnd, now);

            if(lateBy.compareTo(gracePeriod) > 0){
                // Too late — dropped, not added below. Real devices have flaky
                // connectivity, so a grace period exists to absorb normal lateness; this is
                // for the readings that miss even that.
                logger.warn("MetricWindowAggregator >> record >> Late reading for device {} metric {}: window [{} - {}) already closed, arrived {} after close", deviceId, metricType, windowStart, windowEnd, lateBy);
                lateDroppedCounter.increment();
                return ;
            }
            // Late, but within the grace period — still gets added to its correct
            // historical window below. debug, not warn: this is expected, normal jitter,
            // not something worth an operator's attention.
            logger.debug("MetricWindowAggregator >> record >> Late reading for device {} metric {} accepted within grace period ({} late)",
                    deviceId, metricType, lateBy);
            lateAcceptedCounter.increment();
        }

        WindowKey key = new WindowKey(deviceId, metricType, windowStart);
        windows.computeIfAbsent(key, k -> new WindowAccumulator()).add(value);

    }


    // Deliberately a plain get(), not computeIfAbsent — a read shouldn't have the side
    // effect of creating a new empty window just because someone asked about it before any
    // data arrived. See ReadingSvc.getCurrentMetrics for what happens on an empty Optional
    // (a 404, not a zeroed-out response).
    public Optional<WindowAccumulator> getCurrentWindow(String deviceId, String metricType) {
        Instant windowStart = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        WindowKey key = new WindowKey(deviceId, metricType, windowStart);
        return Optional.ofNullable(windows.get(key));
    }

}
