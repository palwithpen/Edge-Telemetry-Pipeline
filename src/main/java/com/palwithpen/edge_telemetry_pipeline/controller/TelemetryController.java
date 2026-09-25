package com.palwithpen.edge_telemetry_pipeline.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;



// A leftover connectivity check from the very first "does the app boot" test. Predates
// ApiResponse, which is why it returns a raw Map instead of the standard envelope — kept
// as-is rather than "fixed," since it's a harmless artifact of how the project started.
 

@RestController
@RequestMapping ("/telemetry")
public class TelemetryController {

    @GetMapping("")
    public Map<String,String> checkConnectivity() {
        return Map.of("status","connected");
    }
    
}
 