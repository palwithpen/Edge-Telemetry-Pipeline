package com.palwithpen.edge_telemetry_pipeline.worker;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.palwithpen.edge_telemetry_pipeline.service.ReadingSvc;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

// This class is deliberately built with TWO interchangeable consumer strategies living
// side by side — processQueue() (a fixed pool of long-lived worker loops) and
// dispatchLoop()/processOne() (one virtual thread per queued item) — toggled by
// commenting/uncommenting below. That's not leftover cruft; it's how the P3 benchmark in
// the README was actually run: same queue, same downstream logic, only the executor and
// consumption pattern change, so the comparison is apples-to-apples.
@Component
public class ReadingWorkerPool {
    private static final Logger logger = LoggerFactory.getLogger(ReadingWorkerPool.class);

    private final BlockingQueue<ParsedReading> readingQueue;
    private final ReadingSvc readingSvc;
    private final ExecutorService executorService;
    private final int poolSize;
    // Caps how many readings can be persisting at once when running the virtual-thread
    // strategy below. Set to 10 to match HikariCP's default connection pool size — without
    // this, "one virtual thread per queued item" happily spins up thousands of them that all
    // try to grab a DB connection simultaneously, most of them time out waiting, and the
    // whole pipeline stalls in a mass-timeout pileup. Learned that one the hard way; see the
    // P3 write-up in the README. Not used by the fixed-pool strategy, which is already
    // naturally capped at poolSize.
    private final Semaphore concurrencyLimiter = new Semaphore(10);

    public ReadingWorkerPool(BlockingQueue<ParsedReading> readingQueue,
        ReadingSvc readingSvc,
        @Value("${worker.pool-size}") int poolSize,
        MeterRegistry meterRegistry) {
        this.readingQueue = readingQueue;
        this.readingSvc = readingSvc;
        this.poolSize = poolSize;
        this.executorService = Executors.newFixedThreadPool(poolSize);
        // this.executorService = Executors.newVirtualThreadPerTaskExecutor();

        // Live queue depth, readable at /actuator/metrics/reading.queue.depth — a Gauge
        // reads the current value on demand rather than needing us to push updates.
        Gauge.builder("reading.queue.depth", readingQueue, BlockingQueue::size)
            .register(meterRegistry);
    }

    @PostConstruct
    public void startWorkers() {
        // executorService.submit(this::dispatchLoop);

        // fixed-pool baseline — uncomment along with processQueue() below to compare
        for (int i = 0; i < poolSize; i++) {
            executorService.submit(this::processQueue);
        }
    }

    // volatile matters here, not just final-vs-not: without it, one thread flipping this to
    // false during shutdown() isn't guaranteed to be visible to the worker threads reading
    // it in their while(running) loops — they could keep looping on a stale cached value.
    private volatile boolean running = true;

    // Virtual-thread strategy: this loop itself stays cheap (just dequeue + hand off), and
    // the actual persist work happens in a freshly submitted virtual thread per item, gated
    // by concurrencyLimiter above.
    private void dispatchLoop() {
        while (running) {
            try {
                ParsedReading parsedReading = readingQueue.poll(1, TimeUnit.SECONDS);
                if(parsedReading == null){
                    continue;
                }
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
            parsedReading.acknowledgment().acknowledge();
        } catch (ResponseStatusException e) {
            logger.warn("ReadingWorkerPool >> processOne >> Rejected reading: {} {}", e.getStatusCode(), e.getReason());
        } catch (Exception e) {
            logger.error("ReadingWorkerPool >> processOne >> Failed to process queued reading", e);
        }
    }

    // Fixed-pool strategy: poolSize long-lived threads, each running this exact loop for
    // the lifetime of the app. poll(1s) instead of take() is what makes graceful shutdown
    // possible — take() blocks forever with nothing to wake it but an interrupt, which would
    // risk cutting a thread off mid-write. Polling lets a worker notice running==false and
    // exit cleanly between items instead.
    private void processQueue() {
        while (running) {
            try {
                ParsedReading parsedReading = readingQueue.poll(1,TimeUnit.SECONDS);
                if(parsedReading == null){
                    continue;
                }
                readingSvc.createReading(parsedReading.deviceId(), parsedReading.request());
                // Only ack after the write actually succeeds. If createReading throws, we
                // fall into the catch blocks below and never reach this line — the message
                // stays unacknowledged, and the broker will redeliver it on our next
                // reconnect. That's the entire at-least-once guarantee, in one line.
                parsedReading.acknowledgment().acknowledge();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (ResponseStatusException e) {
                // A deliberate rejection (e.g. device not found) — not a bug, just bad data.
                // Left unacked on purpose for now: a permanently-invalid reading currently
                // gets redelivered forever, which is a known, accepted gap rather than
                // something quietly papered over. Worth a poison-message policy eventually.
                logger.warn("ReadingWorkerPool >> processQueue >> Rejected reading: {} {}", e.getStatusCode(), e.getReason());
            } catch (Exception e) {
                logger.error("ReadingWorkerPool >> processQueue >> Failed to process queued reading", e);
            }
        }
    }

    // Runs on app shutdown. The goal is "finish what's already in flight, then stop" — not
    // "guarantee the entire queue empties," which could hang shutdown indefinitely under a
    // large backlog. 30s is generous for in-flight work to wrap up; shutdownNow() is the
    // last-resort fallback if something's still stuck past that.
    @PreDestroy
    public void shutdown(){
        logger.info("ReadingWorkerPool >> shutdown >> Stopping workers, draining queue (current size: {})", readingQueue.size());
        running = false;
        executorService.shutdown();

        try {
            // awaitTermination returns true when everything finished cleanly within the
            // timeout, false when it timed out first — easy to get backwards, so spelled
            // out with the ! rather than swapping which branch does what.
            if(!executorService.awaitTermination(30, TimeUnit.SECONDS)){
                logger.warn("ReadingWorkerPool >> shutdown >> Workers did not finish within timeout, forcing shutdown");
                executorService.shutdownNow();
            } else {
                logger.info("ReadingWorkerPool >> shutdown >> All workers drained cleanly");
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executorService.shutdownNow();
        }
    }
}
