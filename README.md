# Edge Telemetry Pipeline

A Spring Boot service for ingesting device telemetry over both HTTP and MQTT, with a
bounded-queue + worker-pool pipeline for decoupling ingestion from processing under load,
and windowed aggregation for turning a firehose of raw readings into something you can
actually look at.

I'm rebuilding my Spring Boot/Java skills after a few years mostly living in Python and
event-driven systems elsewhere (MQTT, ZeroMQ, edge/AI orchestration), and this project is
the vehicle for that — deliberately structured as a series of phases, each one adding a
layer of "how would this actually behave in production" rather than stopping at "it
works on my machine." It's the first of two portfolio projects; the second will pick up
a different kind of hard problem — correctness under concurrency, via event sourcing —
once this one's done.

## Stack

- **Java 21** (virtual threads)
- **Spring Boot 4.1.1** (Spring 7 / Jackson 3 — note this is newer than most tutorials,
  which target Boot 3.x; some dependency names and defaults differ, see notes below)
- **PostgreSQL 16** — persistence, via Spring Data JPA / Hibernate
- **Eclipse Mosquitto 2** — MQTT broker
- **Spring Integration MQTT** (Eclipse Paho client under the hood) — MQTT ingestion
- **springdoc-openapi** — Swagger UI
- **Docker Compose** — local Postgres + Mosquitto

## Architecture

Two ingestion paths feed the same pipeline, and every reading — however it arrives —
also flows into an in-memory windowed aggregator:

```
HTTP POST /devices/{deviceId}/readings ──────────────┐
                                                       ├──> ReadingSvc ──> Postgres
MQTT devices/{deviceId}/readings ──> ReadingMqttListener            │
                                          │                          ▼
                                          ▼                MetricWindowAggregator
                              BlockingQueue<ParsedReading> (bounded)  │
                                          │                           ▼
                                          ▼              GET /devices/{id}/metrics/{type}
                                  ReadingWorkerPool
                              (worker threads draining the queue)
```

The MQTT path is decoupled from database writes by a bounded `BlockingQueue`: the MQTT
listener only parses and validates messages before enqueueing them, and a separate worker
pool drains the queue and performs the actual writes. When the queue fills, the producer
blocks (`put()`) rather than dropping messages — deliberate backpressure, propagating all
the way back to the MQTT client.

`ReadingWorkerPool` supports two interchangeable consumer strategies (toggle by
commenting/uncommenting in the class — see the file for both implementations):

- **Fixed platform thread pool** (`Executors.newFixedThreadPool`) — a small number of
  long-lived worker threads, each looping `take()` → process → `take()`.
- **Virtual threads** (`Executors.newVirtualThreadPerTaskExecutor`) — one virtual thread
  per queued item, with a `Semaphore` capping in-flight concurrency to match the database
  connection pool size (uncapped virtual-thread submission will happily create thousands
  of threads that all contend for the same small connection pool at once).

### Benchmark: fixed pool vs. virtual threads

Measured with `scripts/burst_publish.py` (10,000 messages published as fast as possible,
drain time measured via DB polling), at equal concurrency (10 concurrent in-flight tasks,
matching the default HikariCP pool size):

| Strategy                          | Concurrency | Throughput          |
|------------------------------------|-------------|----------------------|
| Fixed platform thread pool        | 10          | ~3580 readings/sec  |
| Virtual threads (semaphore-capped)| 10          | ~2470 readings/sec  |

At equal, small concurrency bounded by the database connection pool, platform threads
outperformed virtual threads here — each virtual-thread task incurs its own creation/
scheduling overhead, whereas the fixed pool's threads are created once and reused. Virtual
threads' real advantage is supporting far higher concurrency cheaply (thousands of
in-flight, mostly-blocked tasks) rather than outperforming an already-small, well-sized
platform pool. I think that's actually the more interesting finding than "virtual threads
won" would have been — it's a genuine nuance, not the headline the hype would predict. See
the class-level comments in `ReadingWorkerPool` for the full context.

### Windowed aggregation

Every persisted reading — whether it came in over HTTP or MQTT — also gets fed into
`MetricWindowAggregator`, which buckets values into tumbling 1-minute windows keyed by
`(deviceId, metricType, windowStart)` and computes average/p95/count on demand. It's
windowed by **event time** (`recordedAt`, when the device says it happened), not
processing time (`ingestedAt`, when the server got it) — which is what makes "late data"
a meaningful concept at all: a reading is late when it arrives after its window has
already closed, not just when it arrives slowly.

Late data isn't dropped outright — it's accepted into its correct historical window as
long as it's within a configurable grace period (`window.grace-period-seconds`), and only
dropped (with a warning logged) beyond that. It's a small, honest compromise: real devices
have flaky connectivity, and treating "10 seconds late" the same as "would never arrive"
felt wrong.

## Running locally

```bash
docker-compose up -d          # Postgres (5432) + Mosquitto (1883)
./mvnw spring-boot:run
```

The app starts on port `8080` by default (override with `server.port` in
`application.properties`).

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI spec: `http://localhost:8080/v3/api-docs`
- Actuator health: `http://localhost:8080/actuator`

### Configuration

Key properties in `src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/telemetry?reWriteBatchedInserts=true
spring.datasource.username=telemetry
spring.datasource.password=telemetry
spring.jpa.hibernate.ddl-auto=update   # dev convenience only — not for production

mqtt.broker-url=tcp://localhost:1883
mqtt.client-id=edge-telemetry-pipeline-subscriber
mqtt.topic=devices/+/readings

queue.capacity=50          # bounded queue size between MQTT ingestion and DB writes
worker.pool-size=4         # fixed-pool worker count (unused in virtual-thread mode)

window.duration-minutes=1        # tumbling window size for aggregation
window.grace-period-seconds=10   # how late a reading can arrive and still be accepted
```

## API

| Method | Path                                     | Description                          |
|--------|-------------------------------------------|---------------------------------------|
| POST   | `/devices`                                | Register a device                    |
| GET    | `/devices/{deviceId}`                     | Get device details                   |
| PATCH  | `/devices/{deviceId}`                     | Update device name/type (partial)    |
| DELETE | `/devices/{deviceId}`                     | Delete a device                      |
| POST   | `/devices/{deviceId}/readings`            | Submit a reading over HTTP           |
| GET    | `/devices/{deviceId}/metrics/{metricType}`| Current window's avg/p95/count for a metric (404 if no data yet this window) |
| GET    | `/telemetry`                              | Connectivity check                   |

Readings can also be submitted over MQTT by publishing to `devices/{deviceId}/readings`
with a JSON body matching the HTTP request shape:

```json
{"metricType": "temperature", "value": 21.5}
```

All responses are wrapped in a consistent envelope (`ApiResponse<T>`):

```json
{"status": 200, "data": { ... }, "timestamp": "2026-09-24T12:00:00Z"}
```

Errors follow the same shape, with `data` holding either an error message or, for
validation failures, a list of per-field errors — handled centrally in
`GlobalExceptionHandler`.

## Load testing

`scripts/burst_publish.py` bursts N MQTT messages and polls the database to measure
sustained drain throughput — used for the concurrency benchmarking above and for
verifying backpressure behavior under load. It's also how a very real bug got found: an
early run kept stalling partway through a 10,000-message burst, which turned out to be
Mosquitto silently dropping messages past its default per-client queue limit — not a bug
in the app at all, but a broker default that didn't match the scale of the test. That's
now documented as a config setting (`max_queued_messages`) rather than a mystery.

```bash
python3 -m venv .venv && source .venv/bin/activate
pip install paho-mqtt
python3 scripts/burst_publish.py --device-id <existing-device-id> --count 10000
```

## Project roadmap

This is the first of two portfolio projects — this one focuses on ingestion throughput
and concurrency; a second (separate repo, not yet started) will focus on correctness
under concurrency via event sourcing and CQRS. Together they're meant to cover both halves
of the same underlying skill: keeping up with volume, and never getting a concurrent
operation wrong.

- [x] **P1 — REST + JPA baseline.** The unglamorous but necessary foundation: device and
      reading CRUD over HTTP, proper entity/DTO separation, validation at the door,
      centralized error handling. Nothing here is exciting on its own, but it's the part
      that has to be right before anything built on top of it can be trusted.

- [x] **P2 — MQTT ingestion.** Swapped (well, added alongside) HTTP intake for a real MQTT
      subscriber via Spring Integration, mirroring the kind of edge/device-stream ingestion
      I'd previously done in Python. This is also where Spring Boot 4's more aggressive
      module-splitting bit hardest — see the gotchas section below.

- [x] **P3 — Concurrency & backpressure.** The heart of this project's story: decoupling
      MQTT receipt from database writes with a bounded queue and a worker pool, then
      deliberately overwhelming it to prove the backpressure actually works, then
      benchmarking a fixed thread pool against virtual threads to see which one's claims
      hold up under an actual measurement instead of a blog post's assertion.

- [x] **P4 — Windowed aggregation.** Per-device rolling metrics (avg/p95) over 1-minute
      windows, with explicit, deliberate handling of out-of-order and late-arriving data
      rather than pretending every reading shows up exactly when it should.

- [ ] **P5 — Resilience.** Still ahead: at-least-once delivery guarantees, deduplication
      for the inevitable retries, a graceful shutdown that actually drains the queue
      instead of dropping whatever's in flight, and richer Actuator metrics so the system
      can tell you how it's doing instead of you having to guess.

## Notable Spring Boot 4 gotchas hit along the way

Worth keeping a running list of these, since a lot of tutorials and Stack Overflow answers
still assume Boot 3.x, and the failure modes here are quiet rather than loud:

- The web starter is `spring-boot-starter-webmvc`, not `spring-boot-starter-web`.
- Test starters are split per-slice (`spring-boot-starter-webmvc-test`,
  `spring-boot-starter-data-jpa-test`, etc.) instead of one `spring-boot-starter-test`.
- Spring Boot 4 ships **Jackson 3** (`tools.jackson.*`) by default, not Jackson 2
  (`com.fasterxml.jackson.*`) — code and tutorials assuming the latter will fail to find
  an `ObjectMapper` bean of the expected type.
- Auto-configuration was split into many small, focused modules instead of one monolithic
  `spring-boot-autoconfigure` jar. Adding `spring-integration-mqtt` alone is **not**
  enough to get `@EnableIntegration` applied automatically — `spring-boot-starter-integration`
  must be added explicitly, or `@ServiceActivator` methods are silently never wired to
  their channels. This one cost a genuinely confusing debugging session before the root
  cause turned up.
