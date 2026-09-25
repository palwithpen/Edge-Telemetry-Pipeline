package com.palwithpen.edge_telemetry_pipeline.mqtt;

import java.util.concurrent.BlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.StaticMessageHeaderAccessor;
import org.springframework.integration.acks.SimpleAcknowledgment;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;
import com.palwithpen.edge_telemetry_pipeline.dto.ReadingRequest;
import com.palwithpen.edge_telemetry_pipeline.worker.ParsedReading;

// This is the "producer" side of the P3 pipeline: it only receives, parses and validates.
// It deliberately does NOT call ReadingSvc or touch the database itself — that's
// ReadingWorkerPool's job, on its own threads, after pulling off the queue below. Keeping
// this handler fast and non-blocking is the whole point; the DB write is the slow part,
// and it shouldn't happen on Paho's single message-delivery thread.
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

            // Manual acks mean the broker is still waiting to hear "you can stop resending
            // this." We don't say that here — we just carry the acknowledgment callback
            // along with the parsed reading, so whichever worker thread actually persists
            // it can be the one to fire it. See ReadingWorkerPool.processOne/processQueue.
            SimpleAcknowledgment acknowledgment = StaticMessageHeaderAccessor.getAcknowledgment(message);

            // put() blocks if the queue is full — that's deliberate backpressure. If workers
            // can't keep up, this call (and therefore Paho's delivery thread) stalls instead
            // of silently accepting unlimited work we can't actually process.
            readingQueue.put(new ParsedReading(deviceId, request,acknowledgment));
            logger.debug("ReadingMqttListener >> handleReadingMessage >> Queued reading for device {}", deviceId);

        } catch (InterruptedException e) {
            // Restore the interrupt flag — swallowing it here would hide the interruption
            // from anything further up the call stack that might care (e.g. shutdown logic).
            Thread.currentThread().interrupt();
            logger.warn("ReadingMqttListener >> handleReadingMessage >> InterruptedException {}", e.getMessage());

        } catch (Exception e) {
            // Broad on purpose: a bad payload, a full queue timeout, anything else — none of
            // it should crash the MQTT delivery thread. There's no HTTP client waiting on the
            // other end of this to hand an error back to, so logging is the only real option.
            logger.error("ReadingMqttListener >> handleReadingMessage >> Failed to process message on topic {}", topic, e);
        }

    }


    private String extractDeviceId(String topic) {
        String[] parts = topic.split("/");
        return parts[1];
    }
}

