package com.palwithpen.edge_telemetry_pipeline.dto;

import com.palwithpen.edge_telemetry_pipeline.model.DeviceType;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter 
@Setter 
@NoArgsConstructor 
@ToString 
public class DeviceUpdateRequest {
    @Size(max = 128)
    private String deviceName;

    private DeviceType deviceType;
}
