package com.palwithpen.edge_telemetry_pipeline.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable 
@Getter @Setter @NoArgsConstructor 
public class GeoLocation {

    @Column (name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;
}