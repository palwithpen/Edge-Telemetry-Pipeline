package com.palwithpen.edge_telemetry_pipeline.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;



@RestController 
@RequestMapping ("/telemetry")
public class TelemetryController {
    
    @GetMapping("")
    public Map<String,String> checkConnectivity() {
        return Map.of("status","connected");
    }
    
}
 