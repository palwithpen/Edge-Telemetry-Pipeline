package com.palwithpen.edge_telemetry_pipeline.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

@Service 
public class ReadingSvc {

    private static final Logger logger = LoggerFactory.getLogger(ReadingSvc.class);

    public ReadingSvc(DeviceRepo deviceRepo, ReadingRepo readingRepo, MetricWindowAggregator metricWindowAggregator){
        this.deviceRepo = deviceRepo;
        this.readingRepo = readingRepo;
        this.metricWindowAggregator = metricWindowAggregator;
    }
    
    private final DeviceRepo deviceRepo;
    private final ReadingRepo readingRepo;
    private final MetricWindowAggregator metricWindowAggregator;

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
        entity.setRecordedAt(request.getRecordedAt() != null ? request.getRecordedAt() : Instant.now());
        ReadingEntity saved = readingRepo.saveAndFlush(entity);

        logger.debug("ReadingSvc >> createReading >> Saved data in DB");

        metricWindowAggregator.record(deviceId, saved.getMetricType(), saved.getRecordedAt(), saved.getValue());

        return new ReadingResponse(saved);
    }

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
