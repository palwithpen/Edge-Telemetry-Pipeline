package com.palwithpen.edge_telemetry_pipeline.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.palwithpen.edge_telemetry_pipeline.model.ReadingEntity;

public interface ReadingRepo extends JpaRepository<ReadingEntity, Long> {

    // The dedup lookup — used by ReadingSvc.createReading when a save fails the unique
    // constraint on idempotency_key, to fetch what's already there and return it as if this
    // were the first successful save. This used to be
    // findByDevice_IdAndMetricTypeAndRecordedAt (content-based, and it broke — see README).
    Optional<ReadingEntity> findByIdempotencyKey(String idempotencyKey);

}
