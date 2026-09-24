package com.palwithpen.edge_telemetry_pipeline.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter 
@Setter 
@NoArgsConstructor 
@ToString 
public class ReadingRequest {
 
    @NotBlank
    @Size (max=32)
    private String metricType;

    @NotNull 
    private Double value;

    private Instant recordedAt;
}
