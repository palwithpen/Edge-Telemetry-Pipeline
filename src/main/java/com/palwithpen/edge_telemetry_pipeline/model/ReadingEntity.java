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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// idempotency_key's uniqueness is doing real work here, not just documenting intent — it's
// the actual mechanism P5's dedup relies on. See ReadingSvc.createReading: a redelivered
// MQTT message (same idempotencyKey, since the device reuses it on retries) hits this
// constraint, throws, and gets treated as a duplicate rather than a new row.

@Entity
@Table(name="reading", uniqueConstraints = @UniqueConstraint(columnNames = {"idempotency_key"}))
@Getter
@Setter
@NoArgsConstructor
public class ReadingEntity {

    // SEQUENCE, not IDENTITY — IDENTITY forces one-row-at-a-time inserts because the DB has
    // to assign the key before Hibernate knows it, which silently defeats JDBC batching. A
    // pooled sequence (allocationSize 50) lets Hibernate reserve a block of IDs in one
    // round-trip and batch freely. This one actually regressed once mid-project and had to
    // be caught and restored — worth remembering if it ever looks "simplified" back to
    // IDENTITY in a future diff.
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "reading_seq")
    @SequenceGenerator(name = "reading_seq", sequenceName = "reading_seq", allocationSize = 50)
    private Long id;

    // LAZY, and deliberately no reverse @OneToMany<Reading> on DeviceEntity — an eager
    // fetch or a back-reference collection here is exactly how you get an N+1 query problem
    // at telemetry volume without meaning to.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private DeviceEntity device;

    @Column(nullable = false, length = 32)
    private String metricType;

    @Column(nullable = false)
    private double value;

    // When the DEVICE says the measurement happened — comes from the client payload
    // (defaulted to now() server-side only if omitted). This is what MetricWindowAggregator
    // buckets windows by, which is what makes "late data" a real, detectable concept.
    @Column(nullable = false)
    private Instant recordedAt;

    // When WE actually received/stored it. Auto-populated by Hibernate, never set manually.
    // The gap between this and recordedAt is exactly what P4's lateness handling is about.
    @CreationTimestamp
    private Instant ingestedAt;

    // Client-generated, reused identically across retries of "the same" reading. This is
    // what makes dedup work regardless of whether recordedAt happens to be stable — it used
    // to rely on recordedAt alone, and a real bug (see README, P5 section) showed that
    // wasn't safe once the server could default it fresh on every delivery attempt.
    @Column(nullable = false, unique = true)
    private String idempotencyKey;
}