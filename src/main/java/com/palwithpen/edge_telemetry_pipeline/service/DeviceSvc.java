package com.palwithpen.edge_telemetry_pipeline.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.palwithpen.edge_telemetry_pipeline.dto.DeviceRequest;
import com.palwithpen.edge_telemetry_pipeline.dto.DeviceResponse;
import com.palwithpen.edge_telemetry_pipeline.dto.DeviceUpdateRequest;
import com.palwithpen.edge_telemetry_pipeline.model.DeviceEntity;
import com.palwithpen.edge_telemetry_pipeline.model.GeoLocation;
import com.palwithpen.edge_telemetry_pipeline.repository.DeviceRepo;

// Device CRUD — the P1 foundation everything else in this project sits on top of. Nothing
// exciting happens here on purpose; the interesting decisions are more about what ISN'T
// here (no entities leaking out as responses, no field injection, no unvalidated writes)
// than what is.
@Service
public class DeviceSvc {

    private static final Logger logger = LoggerFactory.getLogger(DeviceSvc.class);

    public DeviceSvc(DeviceRepo deviceRepo){
        this.deviceRepo = deviceRepo;
    }

    private final DeviceRepo deviceRepo;

    public boolean checkIfDeviceExists(String deviceId){
        return deviceRepo.existsById(deviceId);
    } 

    public DeviceResponse createDevice(DeviceRequest request){
        logger.debug("DeviceSvc >> createDevice >> Initiated");
        if (checkIfDeviceExists(request.getId())){
            logger.debug("DeviceSvc >> createDevice >> Device details already exists");
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "DEVICE_ALREADY_EXISTS"
            );
        }
        DeviceEntity entity = new DeviceEntity();
        entity.setId(request.getId());
        entity.setDeviceName(request.getDeviceName());
        entity.setDeviceType(request.getDeviceType());
        entity.setSite(request.getSite());

        GeoLocation location = new GeoLocation();
        location.setLatitude(request.getLatitude());
        location.setLongitude(request.getLongitude());

        entity.setLocation(location);

        // The existsById check above is a TOCTOU race waiting to happen: two concurrent
        // requests for the same id can both pass it (neither sees the other's row yet),
        // then both try to insert. This catch is the actual safety net — the DB's own
        // primary-key constraint is what really prevents a duplicate, the check above is
        // just an optimization to fail fast in the common case.
        DeviceEntity saved ;
        try {
            saved = deviceRepo.saveAndFlush(entity);
            logger.debug("DeviceSvc >> createDevice >> Device data saved successfully");

        } catch (DataIntegrityViolationException e) {
            logger.error("DeviceSvc >> createDevice >> Exception occurred", e);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "DEVICE_ALREADY_EXISTS");
        }

        return new DeviceResponse(saved);
    }

    public DeviceResponse getDeviceDetails(String deviceId){
        logger.debug("DeviceSvc >> getDeviceDetails >> Initialized >> Device ID: {}",deviceId);
        DeviceEntity entity = deviceRepo.findById(deviceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "DEVICE_NOT_FOUND"));

        return new DeviceResponse(entity);
    }

    public List<DeviceResponse> getAllDeviceDetails(){
        logger.debug("DeviceSvc >> getAllDeviceDetails >> Initialized");
        return deviceRepo.findAll().stream().map(DeviceResponse::new).toList();
    }

    public void deleteDeviceDetails(String deviceId){
        logger.debug("DeviceSvc >> deleteDeviceDetails >> Initialized >> Device ID: {}",deviceId);
        if (!checkIfDeviceExists(deviceId)){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "DEVICE_NOT_FOUND");
        }
        deviceRepo.deleteById(deviceId);

    }

    // A PATCH, not a PUT — only deviceName/deviceType are updatable, and only the fields
    // actually present in the request get touched (site/location/id are immutable after
    // creation). Each field is null-checked independently rather than requiring both, so a
    // client can update just one without resending the other.
    public DeviceResponse updateDevice(String deviceId, DeviceUpdateRequest request){
        if (request.getDeviceName() == null && request.getDeviceType() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "NO_FIELDS_TO_UPDATE");
        }

        DeviceEntity entity = deviceRepo.findById(deviceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "DEVICE_NOT_FOUND"));
        
        if (request.getDeviceName() != null) {
            if (request.getDeviceName().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "DEVICE_NAME_CANNOT_BE_BLANK");
            }
            entity.setDeviceName(request.getDeviceName());
        }

        if (request.getDeviceType() != null) {
            entity.setDeviceType(request.getDeviceType());
        }

        DeviceEntity saved = deviceRepo.saveAndFlush(entity);
        return new DeviceResponse(saved);

    }
}   
