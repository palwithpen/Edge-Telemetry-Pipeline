package com.palwithpen.edge_telemetry_pipeline.mqtt;

import java.util.concurrent.BlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;
import com.palwithpen.edge_telemetry_pipeline.dto.ReadingRequest;
import com.palwithpen.edge_telemetry_pipeline.worker.ParsedReading;

@Component 
public class ReadingMqttListener {
    
    private final ObjectMapper om;
    private final BlockingQueue<ParsedReading> readingQueue;

    private static final Logger logger = LoggerFactory.getLogger(ReadingMqttListener.class);

    public ReadingMqttListener(BlockingQueue<ParsedReading> readingQueue,ObjectMapper om){
        this.om = om;
        this.readingQueue = readingQueue;
    }

    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void handleReadingMessage(Message<String> message){

        String topic = (String) message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC);
        
        try {
            String deviceId = extractDeviceId(topic);
            ReadingRequest request = om.readValue(message.getPayload(), ReadingRequest.class);

            readingQueue.put(new ParsedReading(deviceId, request));
            logger.debug("ReadingMqttListener >> handleReadingMessage >> Reading saved for device {}", deviceId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("ReadingMqttListener >> handleReadingMessage >> InterruptedException {}", e.getMessage());

        } catch (Exception e) {
            logger.error("ReadingMqttListener >> handleReadingMessage >> Failed to process message on topic {}", topic, e);
        }

    }


    private String extractDeviceId(String topic) {
        String[] parts = topic.split("/");
        return parts[1];
    }
}

