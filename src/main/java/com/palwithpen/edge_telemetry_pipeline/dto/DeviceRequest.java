package com.palwithpen.edge_telemetry_pipeline.dto;

import com.palwithpen.edge_telemetry_pipeline.model.DeviceType;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

// The request body for registering a device. Flat lat/long rather than a nested location
// object — simpler than mirroring GeoLocation's shape 1:1, at the cost of a few extra
// lines wherever this gets mapped to a DeviceEntity (see DeviceSvc.createDevice).
@Getter
@Setter
@NoArgsConstructor
@ToString
public class DeviceRequest {

    @NotBlank
    private  String id;

    @NotBlank 
    private String deviceName;

    @NotNull 
    private DeviceType deviceType;
    
    @NotBlank 
    private String site;
    
    @NotNull 
    @DecimalMin("-90.0") @DecimalMax("90.0")
    private Double latitude;

    @NotNull
    @DecimalMax("180.0") @DecimalMin("-180.0")
    private Double longitude;
}
