package com.palwithpen.edge_telemetry_pipeline.worker;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// This is the seam between ingestion (fast, MQTT) and processing (slow, database writes) —
// the whole reason P3 exists. ArrayBlockingQueue specifically, not LinkedBlockingQueue,
// because we wanted the boundedness to be unambiguous: this queue genuinely cannot grow past
// its capacity, which is the property the backpressure story depends on.
@Configuration
public class ReadingQueueConfig {

    @Value("${queue.capacity}")
    private int QUEUE_CAPACITY;

    @Bean
    public BlockingQueue<ParsedReading> readingQueue() {
        return new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    }
}
