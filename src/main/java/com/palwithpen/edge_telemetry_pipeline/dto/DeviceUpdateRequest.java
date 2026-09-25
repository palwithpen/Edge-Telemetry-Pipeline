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
// PATCH body — every field is optional and null-able on purpose, since only the fields the
// client actually sends should change (see DeviceSvc.updateDevice for the null-checks that
// enforce that). No @NotBlank on deviceName here: that would wrongly make it required on
// every patch. The blank-string check instead lives in the service, since "absent" and
// "explicitly sent as empty" need different handling that validation annotations alone
// can't express cleanly.
public class DeviceUpdateRequest {
    @Size(max = 128)
    private String deviceName;

    private DeviceType deviceType;
}
