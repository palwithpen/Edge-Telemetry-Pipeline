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

    // Client-supplied, not auto-generated — a device brings its own id. That's why
    // DeviceSvc.createDevice has to explicitly check for an existing row before saving,
    // instead of relying on a generated key to always be fresh.
    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 128)
    private String deviceName;

    // STRING, never ORDINAL — ORDINAL stores the enum's position in the declaration, so
    // reordering DeviceType later would silently corrupt every existing row's meaning.
    @Enumerated(EnumType.STRING)
    @Column(length = 32, nullable = false)
    private DeviceType deviceType;

    @Column(nullable = false)
    private String site;

    // @Embeddable, not a separate table — GeoLocation's lat/long flatten straight into this
    // table's columns. No join needed to read a device's location, and it stays a distinct,
    // reusable value object rather than two loose Double fields bolted on here directly.
    @Embedded
    private GeoLocation location;

    @CreationTimestamp 
    private Instant createdAt;

    @UpdateTimestamp  
    private Instant updatedAt;

}