package com.palwithpen.edge_telemetry_pipeline.model;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity 
@Table(name="reading")
@Getter 
@Setter 
@NoArgsConstructor 
public class ReadingEntity {
    
    @Id 
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "reading_seq")
    @SequenceGenerator(name = "reading_seq", sequenceName = "reading_seq", allocationSize = 50)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private DeviceEntity device;
    
    @Column(nullable = false, length = 32)
    private String metricType;

    @Column(nullable = false)
    private double value;

    @Column(nullable = false)
    private Instant recordedAt; 

    @CreationTimestamp
    private Instant ingestedAt;
}
