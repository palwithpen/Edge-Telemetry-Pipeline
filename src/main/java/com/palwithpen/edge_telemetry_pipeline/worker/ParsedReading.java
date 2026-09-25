package com.palwithpen.edge_telemetry_pipeline.worker;

import org.springframework.integration.acks.SimpleAcknowledgment;

import com.palwithpen.edge_telemetry_pipeline.dto.ReadingRequest;

// The one thing that travels from ReadingMqttListener (producer) to ReadingWorkerPool
// (consumer) through the bounded queue. A record because it's just a value that gets handed
// off once and never mutated — nobody downstream should be able to change what a device
// actually said.
//
// acknowledgment rides along here specifically so the MQTT broker doesn't get told
// "delivered" until a worker has actually persisted this reading — see MqttConfig's
// manual-ack setting and ReadingWorkerPool for where it eventually gets called.
public record ParsedReading(String deviceId, ReadingRequest request, SimpleAcknowledgment acknowledgment) {

}
