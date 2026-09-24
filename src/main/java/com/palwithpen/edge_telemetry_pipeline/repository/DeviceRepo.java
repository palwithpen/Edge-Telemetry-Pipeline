package com.palwithpen.edge_telemetry_pipeline.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.palwithpen.edge_telemetry_pipeline.model.DeviceEntity;

public interface DeviceRepo extends JpaRepository<DeviceEntity,String>  {
    
}
