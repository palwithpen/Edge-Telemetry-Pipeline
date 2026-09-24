package com.palwithpen.edge_telemetry_pipeline.dto;

import java.time.Instant;

import com.palwithpen.edge_telemetry_pipeline.model.DeviceEntity;
import com.palwithpen.edge_telemetry_pipeline.model.DeviceType;

import lombok.Getter;

@Getter 
public class DeviceResponse {
    private final String id;
    private final String deviceName;
    private final DeviceType deviceType;
    private final String site;
    private final Double latitude;
    private final Double longitude;
    private final Instant createdAt;
    private final Instant updatedAt;

    public DeviceResponse(DeviceEntity entity){
        this.id = entity.getId();
        this.deviceName = entity.getDeviceName();
        this.site = entity.getSite();
        this.deviceType = entity.getDeviceType();
        this.latitude = entity.getLocation().getLatitude();
        this.longitude = entity.getLocation().getLongitude();
        this.createdAt = entity.getCreatedAt();
        this.updatedAt = entity.getUpdatedAt();
    }

}
