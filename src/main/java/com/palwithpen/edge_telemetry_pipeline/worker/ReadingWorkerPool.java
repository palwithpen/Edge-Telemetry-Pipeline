package com.palwithpen.edge_telemetry_pipeline.worker;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.palwithpen.edge_telemetry_pipeline.service.ReadingSvc;

import jakarta.annotation.PostConstruct;

@Component
public class ReadingWorkerPool {
    private static final Logger logger = LoggerFactory.getLogger(ReadingWorkerPool.class);

    private final BlockingQueue<ParsedReading> readingQueue;
    private final ReadingSvc readingSvc;
    private final ExecutorService executorService;
    private final int poolSize;
    private final Semaphore concurrencyLimiter = new Semaphore(10);

    public ReadingWorkerPool(BlockingQueue<ParsedReading> readingQueue, ReadingSvc readingSvc, @Value("${worker.pool-size}") int poolSize) {
        this.readingQueue = readingQueue;
        this.readingSvc = readingSvc;
        this.poolSize = poolSize;
        this.executorService = Executors.newFixedThreadPool(poolSize);
        // this.executorService = Executors.newVirtualThreadPerTaskExecutor();
    }

    @PostConstruct
    public void startWorkers() {
        // executorService.submit(this::dispatchLoop);

        // fixed-pool baseline — uncomment along with processQueue() below to compare
        for (int i = 0; i < poolSize; i++) {
            executorService.submit(this::processQueue);
        }
    }

    private void dispatchLoop() {
        while (true) {
            try {
                ParsedReading parsedReading = readingQueue.take();
                concurrencyLimiter.acquire();
                executorService.submit(() -> {
                    try {
                        processOne(parsedReading);
                    } finally {
                        concurrencyLimiter.release();
                    }
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void processOne(ParsedReading parsedReading) {
        try {
            readingSvc.createReading(parsedReading.deviceId(), parsedReading.request());
        } catch (ResponseStatusException e) {
            logger.warn("ReadingWorkerPool >> processOne >> Rejected reading: {} {}", e.getStatusCode(), e.getReason());
        } catch (Exception e) {
            logger.error("ReadingWorkerPool >> processOne >> Failed to process queued reading", e);
        }
    }

    private void processQueue() {
        while (true) {
            try {
                ParsedReading parsedReading = readingQueue.take();
                readingSvc.createReading(parsedReading.deviceId(), parsedReading.request());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (ResponseStatusException e) {
                logger.warn("ReadingWorkerPool >> processQueue >> Rejected reading: {} {}", e.getStatusCode(), e.getReason());
            } catch (Exception e) {
                logger.error("ReadingWorkerPool >> processQueue >> Failed to process queued reading", e);
            }
        }
    }
}
