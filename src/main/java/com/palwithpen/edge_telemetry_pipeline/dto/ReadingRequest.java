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

    // Optional — defaults to server receipt time in ReadingSvc if the client omits it. But
    // for anything that might get retried (MQTT with manual acks — see ReadingWorkerPool),
    // supplying a real, stable value is what makes P4's windowing and P5's dedup actually
    // meaningful, rather than the server just guessing "now" on every delivery attempt.
    private Instant recordedAt;

    // Required, unlike recordedAt. Generated once per logical reading by the CLIENT and
    // reused identically on every retry — this is the actual dedup key (see
    // ReadingEntity's unique constraint and ReadingSvc.createReading's duplicate-catch
    // logic). It exists specifically because relying on recordedAt alone for dedup turned
    // out not to be safe — see the README's P5 write-up for the real bug that forced this.
    @NotBlank
    private String idempotencyKey;
}
