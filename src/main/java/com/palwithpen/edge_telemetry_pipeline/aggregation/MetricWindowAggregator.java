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

@Component 
public class MetricWindowAggregator {
    
    private static final Logger logger = LoggerFactory.getLogger(MetricWindowAggregator.class);
    private final int windowMinutes;
    private final Duration gracePeriod;

    public MetricWindowAggregator(
            @Value("${window.duration-minutes}") int windowMinutes,
            @Value("${window.grace-period-seconds}") long gracePeriodSeconds) {
        this.windowMinutes = windowMinutes;
        this.gracePeriod = Duration.ofSeconds(gracePeriodSeconds);
    }

    private final ConcurrentMap<WindowKey, WindowAccumulator> windows = new ConcurrentHashMap<>();

    public void record(String deviceId, String metricType, Instant recordedAt, double value) {
        Instant windowStart = recordedAt.truncatedTo(ChronoUnit.MINUTES);
        Instant windowEnd = windowStart.plus(windowMinutes, ChronoUnit.MINUTES);
        Instant now = Instant.now();

        if (windowEnd.isBefore(now)) {
            Duration lateBy = Duration.between(windowEnd, now);

            if(lateBy.compareTo(gracePeriod) > 0){
                logger.warn("MetricWindowAggregator >> record >> Late reading for device {} metric {}: window [{} - {}) already closed, arrived {} after close", deviceId, metricType, windowStart, windowEnd, lateBy);
                return ;
            }
            logger.debug("MetricWindowAggregator >> record >> Late reading for device {} metric {} accepted within grace period ({} late)",
                    deviceId, metricType, lateBy);
        }

        WindowKey key = new WindowKey(deviceId, metricType, windowStart);
        windows.computeIfAbsent(key, k -> new WindowAccumulator()).add(value);

    }


    public Optional<WindowAccumulator> getCurrentWindow(String deviceId, String metricType) {
        Instant windowStart = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        WindowKey key = new WindowKey(deviceId, metricType, windowStart);
        return Optional.ofNullable(windows.get(key));
    }
    
}
