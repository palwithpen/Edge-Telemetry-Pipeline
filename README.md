# Edge Telemetry Pipeline

A Spring Boot service for ingesting device telemetry over both HTTP and MQTT, with a
bounded-queue + worker-pool pipeline for decoupling ingestion from processing under load,
and windowed aggregation for turning a firehose of raw readings into something you can
actually look at.

I'm rebuilding my Spring Boot/Java skills after a few years mostly living in Python and
event-driven systems elsewhere (MQTT, ZeroMQ, edge/AI orchestration), and this project is
the vehicle for that — deliberately structured as a series of phases, each one adding a
layer of "how would this actually behave in production" rather than stopping at "it
works on my machine." All five planned phases (P1–P5) are done as of this writing — see
the roadmap below for what each one actually covers. It's the first of two portfolio
projects; the second will pick up a different kind of hard problem — correctness under
concurrency, via event sourcing — now that this one's in a solid state.

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

### Delivery guarantees: acks, dedup, and graceful shutdown

The MQTT path is built to survive a crash without silently losing or duplicating data,
which took a real bug to get right (see Load testing below for the story).

- **Manual acknowledgment.** The MQTT subscriber doesn't tell the broker "delivered" the
  moment a message is parsed and queued — it waits until `ReadingWorkerPool` has actually
  persisted the reading to Postgres before calling `acknowledge()`. If the app crashes
  between enqueueing and persisting, the broker (QoS 1, `cleanSession(false)`) redelivers
  the message on reconnect instead of it vanishing into an in-memory queue that no longer
  exists.
- **Idempotency-key dedup.** Manual acks mean redelivery is expected, which means the same
  reading can legitimately arrive twice. Every `ReadingRequest` carries a client-generated
  `idempotencyKey`, enforced unique at the database level (`ReadingEntity`). A redelivered
  duplicate hits that unique constraint, gets caught, and returns the already-persisted
  record instead of double-inserting — and, just as importantly, without double-counting it
  into the windowed aggregate.
- **Graceful shutdown.** `ReadingWorkerPool` stops accepting new work and drains whatever's
  currently queued (up to a bounded timeout) before the JVM exits, instead of abruptly
  killing worker threads mid-write.

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
- Actuator metrics: `http://localhost:8080/actuator/metrics` (see Observability below)

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

management.endpoints.web.exposure.include=health,metrics
```

## Observability

Beyond the default `health` endpoint, four custom Micrometer metrics are exposed at
`/actuator/metrics/{name}`:

| Metric                     | Type    | What it tells you                                  |
|-----------------------------|---------|------------------------------------------------------|
| `reading.queue.depth`       | Gauge   | Current backlog between MQTT ingestion and DB writes |
| `reading.late.accepted`     | Counter | Late readings accepted within the grace period       |
| `reading.late.dropped`      | Counter | Late readings dropped past the grace period          |
| `reading.duplicate`         | Counter | Redelivered readings caught by idempotency dedup     |

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
{
  "metricType": "temperature",
  "value": 21.5,
  "recordedAt": "2026-09-25T10:15:30.123Z",
  "idempotencyKey": "a3f1c2e0-4b9d-4e2a-9c1a-7d6b5e4f3a2b"
}
```

`idempotencyKey` is required — generate it once per logical reading and send the *same*
value on every retry/republish of that reading, so a redelivered duplicate can be
recognized as one instead of inserted twice. `recordedAt` is optional (the server
defaults to receipt time if omitted), but for anything that might be retried, supplying
it is what makes the dedup and late-window-handling logic actually meaningful.

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
verifying backpressure behavior under load. It's also how two very real bugs got found:

- An early run kept stalling partway through a 10,000-message burst, which turned out to
  be Mosquitto silently dropping messages past its default per-client queue limit — not a
  bug in the app at all, but a broker default that didn't match the scale of the test.
  That's now documented as a config setting (`max_queued_messages`) rather than a mystery.
- After adding manual acks and dedup (see above), a burst test produced *more* rows in
  Postgres than messages published. A broker-forced session takeover mid-test caused a
  batch of in-flight messages to be redelivered — expected — but the dedup logic didn't
  catch the duplicates. The original dedup key was `(deviceId, metricType, recordedAt)`,
  and the test payload never set `recordedAt`, so the server defaulted it to
  `Instant.now()` fresh on *every* delivery attempt, including redeliveries — meaning the
  one field the dedup key relied on being stable was actually different every time. Fixed
  by switching to an explicit, client-generated `idempotencyKey` that stays identical
  across retries by construction, rather than depending on a timestamp to happen to
  collide.

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

- [x] **P1 — REST + JPA baseline.** Device and reading CRUD over HTTP — unglamorous, but
      every decision here is load-bearing for everything built on top of it later.
      - **Entities never leave the service layer.** `DeviceResponse`/`ReadingResponse` are
        hand-built, read-only DTOs, not the JPA entities themselves. Returning entities
        directly would couple the API's shape to the database schema (a column rename
        becomes a breaking API change), risk leaking Hibernate proxy internals through
        Jackson, and — concretely, for `ReadingEntity` — risk a `LazyInitializationException`
        the moment a `@ManyToOne` association got serialized outside an open session.
      - **`Reading.id` uses a pooled `SEQUENCE`, not `IDENTITY`.** `IDENTITY` forces
        Hibernate to insert one row at a time, because the database has to assign the key
        before Hibernate can know it — which silently defeats JDBC batching no matter what
        else is configured. A pooled sequence (`allocationSize = 50`) lets Hibernate reserve
        a block of IDs in one round-trip and batch inserts freely. This one regressed at
        least once during the project (someone — me — pasted the wrong strategy back in)
        and had to be caught and restored.
      - **Constructor injection everywhere, no `@Autowired` fields.** Not because field
        injection doesn't work — it does — but because constructor injection gives you
        `final` fields, a class whose dependencies are declared where anyone can see them,
        and something you can `new` up directly in a test without a Spring context.
      - **Validation lives on the DTO, never the entity.** A bad request should be rejected
        at the door with a 400 before it's ever turned into an entity, not discovered three
        layers deeper as a database constraint violation.

- [x] **P2 — MQTT ingestion.** Added a real MQTT subscriber (Spring Integration, Eclipse
      Paho underneath) alongside the existing HTTP intake, mirroring the kind of
      edge/device-stream ingestion I'd previously built in Python.
      - **Spring Integration over raw Paho**, even though raw Paho would have meant writing
        my own reconnect/backpressure handling by hand (arguably more interesting to build).
        Chose the adapter because P3 and P5 were always going to need production-grade
        reconnection and manual-ack semantics, and Spring Integration's already solved that
        — better to spend the interesting-code budget on the queue/worker-pool/dedup logic
        that's actually specific to this project, not on re-deriving MQTT plumbing that's a
        solved problem.
      - **`config` (bean wiring) is a separate package from `mqtt` (message-handling logic)**
        — the same instinct as keeping DTOs separate from services. A `@Configuration` class
        should only wire things together; the `@ServiceActivator` that actually parses
        payloads and makes business decisions is a different kind of concern and lives
        elsewhere.
      - **This is also where Spring Boot 4's module-splitting caused a real, silent bug**
        — adding `spring-integration-mqtt` alone wasn't enough to get `@EnableIntegration`
        applied, so the `@ServiceActivator` was never wired to its channel and every message
        vanished with "dispatcher has no subscribers." See the gotchas section below for the
        full diagnosis.

- [x] **P3 — Concurrency & backpressure.** The heart of this project's story: decoupling
      MQTT receipt from database writes with a bounded queue and a worker pool.
      - **`ArrayBlockingQueue`, not `LinkedBlockingQueue`** — a hard, fixed-capacity bound,
        chosen deliberately over a queue that's *technically* boundable but more commonly
        used unbounded by default. The whole point of this phase was proving backpressure
        actually happens, so the boundedness needed to be unambiguous.
      - **Block the producer when the queue is full**, rather than dropping the newest
        message or evicting the oldest. Telemetry data that's merely late is still useful;
        telemetry data that's silently discarded isn't recoverable. Blocking `put()`
        propagates the slowdown all the way back to the MQTT client, which is exactly the
        point — the system should visibly push back, not quietly lose data.
      - **The queue holds a parsed, validated `ParsedReading`, not the raw MQTT message.**
        JSON parsing happens on the producer side, before enqueueing, so a malformed payload
        fails fast instead of occupying a queue slot and a worker cycle only to fail later.
      - **Virtual threads aren't free concurrency — this one caused a real incident.** An
        early "one virtual thread per queued item, no cap" version looked fine at a small
        burst size, then completely stalled at 10,000 messages: thousands of virtual threads
        all raced for HikariCP's 10-connection pool simultaneously, most timed out waiting
        30s for a connection, and the pipeline froze in a mass-timeout pileup. The fix was a
        `Semaphore` capping in-flight concurrency to match the DB pool size — the real lesson
        being that virtual threads move the bottleneck from thread count to whatever
        constrained resource they're all contending for; they don't remove the bottleneck.
      - **The fixed-pool vs. virtual-threads benchmark was run twice, on purpose** — the
        first comparison (4-worker fixed pool vs. 10-concurrency virtual threads) mixed up
        two variables at once and produced a number that couldn't be trusted. Re-running
        both at equal concurrency (10) showed platform threads actually *winning* at that
        scale — a more honest and more interesting result than "virtual threads win," since
        it shows their advantage is in supporting much higher concurrency cheaply, not in
        beating an already-well-sized pool.

- [x] **P4 — Windowed aggregation.** Per-device rolling metrics (avg/p95) over 1-minute
      windows.
      - **Windowed by event time (`recordedAt`), not processing time (`ingestedAt`).** This
        is the one decision the entire phase hinges on — bucketing by *when the server
        received it* would make "late data" a meaningless concept, since nothing could ever
        arrive after its own window if the window is defined by arrival. Event-time
        windowing is what makes lateness something you can actually detect and reason about.
      - **A grace period, not a hard cutoff, for late data.** The first version dropped
        anything arriving after its window closed, or accepted everything unconditionally —
        neither felt right. Real devices have flaky connectivity; treating "10 seconds late"
        identically to "would never arrive" was the wrong tradeoff. Landed on: accept within
        a configurable grace window, drop (with a metric) beyond it.
      - **`CopyOnWriteArrayList` inside `WindowAccumulator`**, specifically because reads
        (computing average/p95, which iterate the whole list) never block writers and vice
        versa — at the cost of every `add()` copying the underlying array, which is a
        known, accepted limitation at this window size and would need revisiting (a
        streaming percentile estimator, e.g.) at much higher per-window volume.
      - **`getCurrentWindow` returns `Optional`, and the controller returns 404 on empty**
        rather than a zeroed-out response — an empty window isn't a real answer, it's the
        absence of one, and pretending otherwise would hide the difference between "no data
        yet" and "the average really is zero."

- [x] **P5 — Resilience.** True end-to-end at-least-once delivery, deduplication, graceful
      shutdown, and observability — the phase where a genuine, non-obvious distributed-
      systems bug got found and fixed, not just simulated for the write-up.
      - **Manual MQTT acknowledgment, not the default auto-ack.** The default acks the
        broker as soon as a message is *enqueued*, which only guarantees "the broker won't
        resend it" — not "it's actually durable." Since the internal queue is in-memory, a
        crash between enqueue and persist would lose the reading while the broker believed
        it was delivered. Moving the ack to *after* the DB write closes that gap.
      - **Manual acks create duplicate deliveries by design** (a crash before acking means
        the broker redelivers on reconnect), which is why dedup had to follow immediately —
        the two aren't independent features, the first one *requires* the second.
      - **Dedup had to be durable (DB-backed), not an in-memory cache — and this was a
        deliberate rejection, not an oversight.** Redelivery only happens *after* a
        reconnect, i.e. after whatever in-memory state existed before the crash is already
        gone. A cache would fail to catch exactly the case it exists to catch.
      - **The dedup key changed mid-phase, because the first version genuinely broke.**
        Started with `(deviceId, metricType, recordedAt)` — content-based, no API change
        needed. A load test then produced more DB rows than messages published: a
        broker-forced session takeover caused a real redelivery, but the test payload never
        set `recordedAt`, so the server defaulted it fresh on every delivery attempt
        *including retries* — the one field the key depended on being stable never was.
        Replaced it with an explicit, client-generated `idempotencyKey`, which stays
        identical across retries by construction instead of by hoping a timestamp collides.
      - **Graceful shutdown polls instead of blocking on `take()`.** A worker blocked
        indefinitely on an empty queue can't check "should I still be running" without being
        forcibly interrupted — which risks cutting off an in-flight write. Swapping to a
        1-second `poll()` with a `volatile running` flag lets workers notice a shutdown
        signal within a bounded window and exit between items, not mid-item.

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
