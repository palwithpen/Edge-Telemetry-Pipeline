package com.palwithpen.edge_telemetry_pipeline.model;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table (name = "device") 
@Getter @Setter
@NoArgsConstructor 
public class DeviceEntity {
    
    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 128)
    private String deviceName;

    @Enumerated(EnumType.STRING)
    @Column(length = 32, nullable = false)
    private DeviceType deviceType;

    @Column(nullable = false)
    private String site;

    @Embedded  
    private GeoLocation location;

    @CreationTimestamp 
    private Instant createdAt;

    @UpdateTimestamp  
    private Instant updatedAt;

}