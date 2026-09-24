package com.palwithpen.edge_telemetry_pipeline.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.palwithpen.edge_telemetry_pipeline.dto.ApiResponse;
import com.palwithpen.edge_telemetry_pipeline.dto.DeviceRequest;
import com.palwithpen.edge_telemetry_pipeline.dto.DeviceResponse;
import com.palwithpen.edge_telemetry_pipeline.dto.DeviceUpdateRequest;
import com.palwithpen.edge_telemetry_pipeline.service.DeviceSvc;

import jakarta.validation.Valid;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;




@RestController 
@RequestMapping("/devices")
public class DeviceController {

        private static final Logger logger = LoggerFactory.getLogger(DeviceController.class);


    public DeviceController( DeviceSvc deviceSvc){
        this.deviceSvc = deviceSvc;
    }

    private final DeviceSvc deviceSvc;

    @PostMapping("")
    public ResponseEntity<ApiResponse<DeviceResponse>> createDevice( @Valid @RequestBody DeviceRequest req) {
        logger.debug("DeviceController >> createDevice >> API Initialized");
        DeviceResponse response = deviceSvc.createDevice(req);
        return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(new ApiResponse<>(HttpStatus.CREATED.value(), response));
    }
    
    @GetMapping("")
    public ResponseEntity<ApiResponse<List<DeviceResponse>>> getAllDeviceDetails() {
        List<DeviceResponse> response = deviceSvc.getAllDeviceDetails();
        return  ResponseEntity
        .status(HttpStatus.OK)
        .body(new ApiResponse<>(HttpStatus.OK.value(), response));
    }
    
    
    @GetMapping("/{deviceId}")
    public ResponseEntity<ApiResponse<DeviceResponse>> getDeviceDetails(@PathVariable String deviceId){
        logger.debug("DeviceController >> getDeviceDetails >> API Initialized");
        DeviceResponse response = deviceSvc.getDeviceDetails(deviceId);
        return ResponseEntity
        .status(HttpStatus.OK)
        .body(new ApiResponse<>(HttpStatus.OK.value(),response));
        
    }

    @DeleteMapping("/{deviceId}")
    public ResponseEntity<ApiResponse<Void>> deleteDeviceDetails(@PathVariable String deviceId) {
        logger.debug("DeviceController >> deleteDeviceDetails >> Device ID:{}", deviceId);
        deviceSvc.deleteDeviceDetails(deviceId);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), null));
    }

    @PatchMapping("/{deviceId}")
    public ResponseEntity<ApiResponse<DeviceResponse>> updateDeviceDetails(@PathVariable String deviceId, @Valid @RequestBody DeviceUpdateRequest request){
        logger.debug("DeviceController >> updateDeviceDetails >> Initialized >> Device ID:{}, Payload: {}",deviceId,request);
        DeviceResponse response = deviceSvc.updateDevice(deviceId, request);
        return ResponseEntity
        .status(HttpStatus.OK)
        .body(new ApiResponse<>(HttpStatus.OK.value(), response));
    }

}
