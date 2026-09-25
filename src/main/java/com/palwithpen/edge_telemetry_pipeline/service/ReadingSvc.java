package com.palwithpen.edge_telemetry_pipeline.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.palwithpen.edge_telemetry_pipeline.aggregation.MetricWindowAggregator;
import com.palwithpen.edge_telemetry_pipeline.aggregation.WindowAccumulator;
import com.palwithpen.edge_telemetry_pipeline.dto.MetricsResponse;
import com.palwithpen.edge_telemetry_pipeline.dto.ReadingRequest;
import com.palwithpen.edge_telemetry_pipeline.dto.ReadingResponse;
import com.palwithpen.edge_telemetry_pipeline.model.DeviceEntity;
import com.palwithpen.edge_telemetry_pipeline.model.ReadingEntity;
import com.palwithpen.edge_telemetry_pipeline.repository.DeviceRepo;
import com.palwithpen.edge_telemetry_pipeline.repository.ReadingRepo;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@Service 
public class ReadingSvc {

    private static final Logger logger = LoggerFactory.getLogger(ReadingSvc.class);

    public ReadingSvc(DeviceRepo deviceRepo, 
        ReadingRepo readingRepo, 
        MetricWindowAggregator metricWindowAggregator,
        MeterRegistry meterRegistry){
        this.deviceRepo = deviceRepo;
        this.readingRepo = readingRepo;
        this.metricWindowAggregator = metricWindowAggregator;
        this.duplicateCounter = Counter.builder("reading.duplicate").register(meterRegistry);

    }
    
    private final DeviceRepo deviceRepo;
    private final ReadingRepo readingRepo;
    private final MetricWindowAggregator metricWindowAggregator;
    private final Counter duplicateCounter;

    // Called from both the HTTP endpoint AND ReadingWorkerPool (for MQTT-sourced readings)
    // — this is the one place all ingestion paths converge, which is why the windowed
    // aggregation and dedup logic both live here rather than duplicated per transport.
    public ReadingResponse createReading(String deviceId, ReadingRequest request){

        logger.debug("ReadingSvc >> createReading >> Initialized");
        logger.debug("ReadingSvc >> createReading >> Payload: {}",request);
        logger.debug("ReadingSvc >> createReading >> Device ID: {}",deviceId);

        DeviceEntity deviceData = deviceRepo.findById(deviceId)
        .orElseThrow(()-> new ResponseStatusException(HttpStatus.NOT_FOUND, "DEVICE_NOT_FOUND"));

        ReadingEntity entity = new ReadingEntity();
        entity.setMetricType(request.getMetricType());
        entity.setValue(request.getValue());
        entity.setDevice(deviceData);
        entity.setIdempotencyKey(request.getIdempotencyKey());
        entity.setRecordedAt(request.getRecordedAt() != null ? request.getRecordedAt() : Instant.now());
        ReadingEntity saved;

        try {
            saved = readingRepo.saveAndFlush(entity);
            logger.debug("ReadingSvc >> createReading >> Saved data in DB");
            // Only feed the aggregator on a genuine new save — see the catch block below
            // for why a duplicate must NOT reach this line (it would double-count a
            // redelivered reading into the average/p95).
            metricWindowAggregator.record(deviceId, saved.getMetricType(), saved.getRecordedAt(), saved.getValue());

        } catch (DataIntegrityViolationException e) {
            // idempotencyKey has a unique DB constraint (ReadingEntity), so this fires when
            // the same reading is redelivered — expected behavior once manual MQTT acks are
            // in play (see ReadingWorkerPool/ReadingMqttListener), not a bug. Treated as
            // success: fetch what's already there and hand it back, so the caller (and the
            // worker's ack) proceeds as if this were the first time. Note we deliberately do
            // NOT call metricWindowAggregator.record() here — that already happened the
            // first time this reading came through.
            logger.warn("ReadingSvc >> createReading >> Duplicate reading for device {} metric {} at {}, returning existing record",
            deviceId, request.getMetricType(), entity.getRecordedAt());
            duplicateCounter.increment();
            saved = readingRepo.findByIdempotencyKey(request.getIdempotencyKey())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "DUPLICATE_LOOKUP_FAILED"));

        }

        return new ReadingResponse(saved);
    }

    // Returns the in-progress window's stats, or 404 if nothing's landed in it yet — a 404
    // here means "no data," not "the average is zero," which is a real difference we don't
    // want to hide behind a zeroed-out response.
    public MetricsResponse getCurrentMetrics(String deviceId, String metricType){
        logger.debug("ReadingSvc >> getCurrentMetrics >> Device ID: {}, Metric: {}", deviceId, metricType);
    
        WindowAccumulator window = metricWindowAggregator.getCurrentWindow(deviceId, metricType)
        .orElseThrow(()-> new ResponseStatusException(HttpStatus.NOT_FOUND, "NO_DATA_FOR_CURRENT_WINDOW"));
    
        return new MetricsResponse(deviceId, 
            metricType, 
            Instant.now().truncatedTo(ChronoUnit.MINUTES), 
            window.average(), 
            window.p95(), 
            window.count()
        );
    }

}
