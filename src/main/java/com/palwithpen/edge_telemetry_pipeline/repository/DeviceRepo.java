package com.palwithpen.edge_telemetry_pipeline.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.palwithpen.edge_telemetry_pipeline.model.DeviceEntity;

// No custom queries needed yet — existsById/findById/deleteById from JpaRepository cover
// everything DeviceSvc currently does. Spring Data generates the implementation at runtime;
// no @Repository annotation needed either, it auto-detects interfaces like this one.

public interface DeviceRepo extends JpaRepository<DeviceEntity,String>  {

}