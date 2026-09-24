package com.palwithpen.edge_telemetry_pipeline.worker;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReadingQueueConfig {

    @Value("${queue.capacity}") 
    private int QUEUE_CAPACITY;

    @Bean
    public BlockingQueue<ParsedReading> readingQueue() {
        return new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    }
}
