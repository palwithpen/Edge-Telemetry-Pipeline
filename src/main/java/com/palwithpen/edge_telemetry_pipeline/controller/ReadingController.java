package com.palwithpen.edge_telemetry_pipeline.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.palwithpen.edge_telemetry_pipeline.dto.ApiResponse;
import com.palwithpen.edge_telemetry_pipeline.dto.MetricsResponse;
import com.palwithpen.edge_telemetry_pipeline.dto.ReadingRequest;
import com.palwithpen.edge_telemetry_pipeline.dto.ReadingResponse;
import com.palwithpen.edge_telemetry_pipeline.service.ReadingSvc;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;


// The HTTP-side entry point into ReadingSvc.createReading — the MQTT side goes through
// ReadingWorkerPool instead, but both end up calling the exact same service method, which
// is the whole reason business logic lives in the service and not scattered across
// transports.

@RestController 
@RequestMapping("/devices/{deviceId}")
public class ReadingController {

    private static final Logger logger = LoggerFactory.getLogger(ReadingController.class);
    
    public ReadingController(ReadingSvc readingSvc){
        this.readingSvc = readingSvc;
    }
    private final ReadingSvc readingSvc;

     @PostMapping("/readings")
    public ResponseEntity<ApiResponse<ReadingResponse>> createReading(@PathVariable String deviceId, @Valid @RequestBody ReadingRequest request) {
        logger.debug("ReadingController >> createReading >> Device ID: {}", deviceId);
        ReadingResponse response = readingSvc.createReading(deviceId, request);
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(new ApiResponse<>(HttpStatus.CREATED.value(), response));
    }

    @GetMapping("/metrics/{metricType}")
    public ResponseEntity<ApiResponse<MetricsResponse>> getMetricsByMetricType(@PathVariable String deviceId, @PathVariable String metricType) {
        logger.debug("ReadingController >> getMetricsByMetricType >> Device ID: {}, Metric: {}", deviceId, metricType);

        MetricsResponse response = readingSvc.getCurrentMetrics(deviceId, metricType);

        return ResponseEntity
        .status(HttpStatus.OK)
        .body(new ApiResponse<>(HttpStatus.OK.value(), response));
    }
    

    
    
}
