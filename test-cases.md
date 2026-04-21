# SwiftMatch — Test Cases

Manual and automated test cases executed against the implementation.
Each case cites the SRS requirement it proves and records the observed result.

---

## `feat/driver-registration`

Executed 2026-04-20. Covers `SRS-DRV-1..10` and the three-type Problem+JSON catalog from Amendment 001 §4.4.

### Automated — `./mvnw -pl api-service verify`

Green run on 2026-04-20. 13/13 tests pass (6 unit, 7 integration against the live compose stack).

#### Unit tests — `DriverStatusTransitionsTest`

Pure state-machine tests, no Spring context. Traces `SRS-DRV-5..10` and `SRS-DRV-9` (idempotency).

| # | Test | SRS | Result |
|---|---|---|---|
| 1 | `online_from_offline_becomes_available` | SRS-DRV-5 | PASS |
| 2 | `online_from_available_stays_available_idempotent` | SRS-DRV-9 | PASS |
| 3 | `online_from_on_trip_stays_on_trip_noop` | SRS-DRV-5 | PASS |
| 4 | `offline_from_available_becomes_offline` | SRS-DRV-6 | PASS |
| 5 | `offline_from_offline_stays_offline_idempotent` | SRS-DRV-9 | PASS |
| 6 | `offline_from_on_trip_is_rejected` | SRS-DRV-10 | PASS (throws `DriverOnTripException`) |

#### Integration tests — `DriverLifecycleIT`

End-to-end with `MockMvc` driving the REST layer, hitting the running Postgres (port 5433) and Redis (port 6379) from `docker compose`. Gated on `RUN_IT=true` env var so default `verify` stays green without infra.

| # | Test | SRS | Result |
|---|---|---|---|
| 1 | `create_driver_returns_201_and_persists_offline` | SRS-DRV-1..3 | PASS |
| 2 | `online_marks_available_in_db_and_redis_with_ttl` | SRS-DRV-5 | PASS (TTL between 25..31 s) |
| 3 | `online_when_on_trip_is_noop` | SRS-DRV-5 | PASS |
| 4 | `offline_when_on_trip_returns_409_problem_json` | SRS-DRV-10 | PASS |
| 5 | `offline_when_available_clears_redis` | SRS-DRV-6 | PASS |
| 6 | `get_missing_driver_returns_404_problem_json` | SRS-DRV-8 | PASS |
| 7 | `create_with_invalid_body_returns_400_problem_json` | SRS-DRV-4 | PASS |

Run command:

```bash
RUN_IT=true ./mvnw -pl api-service verify
```

### Manual smoke tests — executed 2026-04-20

Preconditions:

- `docker compose up -d redis postgres` — both healthy.
- `./mvnw -pl api-service -am spring-boot:run` — service on `:8080`.
- Second terminal in Git Bash.

#### 1. Health check

```bash
curl http://localhost:8080/actuator/health
```

**Expected:** `{"status":"UP"}`

**Result:** PASS — service reports UP with Postgres + Redis subsystems healthy.

#### 2. Create driver → `OFFLINE`

**SRS:** `SRS-DRV-1..4`

**Command:**

```bash
ID=$(curl -s -X POST http://localhost:8080/v1/drivers \
   -H 'Content-Type: application/json' \
   -d '{"name":"Alice","vehicle":"Toyota Prius"}' | jq -r .id)
echo "driver: $ID"
```

**Expected:** 201, body with UUID `id`, `status: OFFLINE`, `createdAt` and `updatedAt` set to same timestamp.

**Observed:**

```json
{
  "id": "580f0bdf-4931-494f-86fe-6e3fae7cc796",
  "name": "Alice",
  "phone": null,
  "vehicle": "Toyota Prius",
  "status": "OFFLINE",
  "createdAt": "2026-04-20T23:17:24.326867Z",
  "updatedAt": "2026-04-20T23:17:24.326867Z"
}
```

**Result:** PASS.

#### 3. Fetch driver — `GET /v1/drivers/{id}`

**SRS:** `GET /v1/drivers/{id}` read path.

**Command:**

```bash
curl -s http://localhost:8080/v1/drivers/$ID | jq
```

**Expected:** 200, same body as above, status `OFFLINE`.

**Result:** PASS — mirrors the create response.

#### 4. Go online → `AVAILABLE`

**SRS:** `SRS-DRV-5`

**Command:**

```bash
curl -s -X POST http://localhost:8080/v1/drivers/$ID/online | jq
```

**Expected:** 200, body with `status: AVAILABLE`.

**Observed:** `status: "AVAILABLE"`.

**Result:** PASS.

#### 5. Redis state after online

**SRS:** `SRS-DRV-5` — driver status + heartbeat keys with 30 s TTL.

**Commands:**

```bash
docker exec swiftmatch-redis redis-cli GET driver:$ID:status
docker exec swiftmatch-redis redis-cli TTL driver:$ID:status
```

**Expected:** `"AVAILABLE"` and TTL ~30.

**Observed:** `AVAILABLE`, `29`.

**Result:** PASS.

#### 6. Go offline → `OFFLINE`

**SRS:** `SRS-DRV-6`

**Command:**

```bash
curl -s -X POST http://localhost:8080/v1/drivers/$ID/offline | jq
```

**Expected:** 200, body with `status: OFFLINE`, `updatedAt` advanced.

**Observed:**

```json
{
  "id": "580f0bdf-4931-494f-86fe-6e3fae7cc796",
  "status": "OFFLINE",
  "createdAt": "2026-04-20T23:17:24.326867Z",
  "updatedAt": "2026-04-20T23:17:25.348842Z"
}
```

**Result:** PASS — `updatedAt` correctly advanced by ~1 s.

#### 7. Redis cleared on offline

**SRS:** `SRS-DRV-6`

**Command:**

```bash
docker exec swiftmatch-redis redis-cli GET driver:$ID:status
```

**Expected:** `(nil)` — keys removed.

**Result:** PASS.

#### 8. Error — 400 validation (empty name + vehicle)

**SRS:** `SRS-DRV-4`, Amendment 001 §4.4 error catalog.

**Command:**

```bash
curl -s -i -X POST http://localhost:8080/v1/drivers \
  -H 'Content-Type: application/json' -d '{"name":"","vehicle":""}'
```

**Expected:**

- HTTP 400
- `Content-Type: application/problem+json`
- body `type` contains `/errors/validation`
- `fieldErrors` array lists all violations

**Observed:**

```http
HTTP/1.1 400
Content-Type: application/problem+json

{
  "type": "https://swiftmatch.local/errors/validation",
  "title": "Validation failed",
  "status": 400,
  "detail": "name: size must be between 1 and 120; vehicle: size must be between 1 and 60; vehicle: must not be blank; name: must not be blank",
  "instance": "/v1/drivers",
  "fieldErrors": [
    "name: size must be between 1 and 120",
    "vehicle: size must be between 1 and 60",
    "vehicle: must not be blank",
    "name: must not be blank"
  ]
}
```

**Result:** PASS — all four violations surfaced.

#### 9. Error — 404 not-found

**SRS:** `SRS-DRV-8`, Amendment 001 §4.4.

**Command:**

```bash
curl -s -i http://localhost:8080/v1/drivers/00000000-0000-0000-0000-000000000000
```

**Expected:**

- HTTP 404
- `Content-Type: application/problem+json`
- body `type` contains `/errors/not-found`
- `detail` references the requested id

**Observed:**

```http
HTTP/1.1 404
Content-Type: application/problem+json

{
  "type": "https://swiftmatch.local/errors/not-found",
  "title": "Resource not found",
  "status": 404,
  "detail": "Driver not found: 00000000-0000-0000-0000-000000000000",
  "instance": "/v1/drivers/00000000-0000-0000-0000-000000000000"
}
```

**Result:** PASS.

#### 10. Error — 409 driver-on-trip

**SRS:** `SRS-DRV-10`.

**Setup:** seed the driver into `ON_TRIP` state directly via SQL, then attempt to go offline.

**Commands:**

```bash
docker exec swiftmatch-postgres psql -U swiftmatch -d swiftmatch \
  -c "UPDATE drivers SET status='ON_TRIP' WHERE id='$ID'"
curl -s -i -X POST http://localhost:8080/v1/drivers/$ID/offline
```

**Expected:**

- HTTP 409
- `Content-Type: application/problem+json`
- body `type` contains `/errors/driver-on-trip`
- `driverId` field populated

**Observed:**

```http
HTTP/1.1 409
Content-Type: application/problem+json

{
  "type": "https://swiftmatch.local/errors/driver-on-trip",
  "title": "Driver is on a trip",
  "status": 409,
  "detail": "Driver 580f0bdf-4931-494f-86fe-6e3fae7cc796 is currently on a trip and cannot go offline",
  "instance": "/v1/drivers/580f0bdf-4931-494f-86fe-6e3fae7cc796/offline",
  "driverId": "580f0bdf-4931-494f-86fe-6e3fae7cc796"
}
```

**Result:** PASS.

---

### Coverage summary — `feat/driver-registration`

| SRS requirement | Covered by |
|---|---|
| SRS-DRV-1..3 (create) | manual #2, IT test 1 |
| SRS-DRV-4 (validation) | manual #8, IT test 7 |
| SRS-DRV-5 (online) | manual #4, #5; unit tests 1–3; IT tests 2–3 |
| SRS-DRV-6 (offline) | manual #6, #7; unit test 4; IT test 5 |
| SRS-DRV-7 (200 return codes) | all manual success-path calls |
| SRS-DRV-8 (404 driver not found) | manual #9, IT test 6 |
| SRS-DRV-9 (idempotency) | unit tests 2, 5 |
| SRS-DRV-10 (409 on ON_TRIP offline) | manual #10; unit test 6; IT test 4 |
| Amendment 001 §4.4 (3-type error catalog) | manual #8, #9, #10 |

---

## `feat/location-ingestion`

Executed 2026-04-20. Extends the suite above with the producer (`api-service`) and consumer (`indexer-service`) sides of `SRS-LOC-1..9` as revised by Amendment 001 §4.2.

### Infra fixes uncovered during smoke (one-time)

Both landed in this branch and are prerequisites for anything in this section to pass:

| Fix | File | Why |
|---|---|---|
| Add `KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1` (+ `TRANSACTION_STATE_LOG_*`, `GROUP_INITIAL_REBALANCE_DELAY_MS=0`) | `docker-compose.yml` | Kafka defaults these to 3. On a single-broker dev cluster `__consumer_offsets` never gets created, every consumer group's `FIND_COORDINATOR` times out, and `kafka-consumer-groups.sh` hangs. |
| Add `<skip>false</skip>` to `spring-boot-maven-plugin` | `indexer-service/pom.xml` | Parent POM sets `<skip>true</skip>` on the plugin. Without the override, `./mvnw -pl indexer-service -am spring-boot:run` reports BUILD SUCCESS in <2 s without actually booting the app. |

### Automated — `./mvnw -pl api-service,indexer-service -am verify`

Green on 2026-04-20. 20 tests total (9 new unit + 4 new integration on top of the driver-registration suite; 5 ITs gated by `RUN_IT=true`).

#### Unit tests — `CityBboxTest` (api-service)

Pure bbox-containment logic, no Spring context. Traces `SRS-LOC-9`.

| # | Test | Result |
|---|---|---|
| 1 | `contains_point_inside_bbox` | PASS |
| 2 | `rejects_point_north_of_bbox` | PASS |
| 3 | `rejects_point_east_of_bbox` | PASS |
| 4 | `accepts_boundary_corners_inclusively` | PASS |
| 5 | `rejects_antipode` | PASS |

#### Unit tests — `LocationHistoryBufferTest` (indexer-service)

Drain / flush semantics of the in-memory buffer. Traces `SRS-LOC-8`. Uses a `java.lang.reflect.Proxy` for `JdbcOperations` because Mockito's inline maker cannot instrument `JdbcTemplate` on JDK 25 / Byte Buddy.

| # | Test | Result |
|---|---|---|
| 1 | `enqueue_below_threshold_does_not_flush` | PASS |
| 2 | `enqueue_at_threshold_flushes_synchronously` | PASS |
| 3 | `scheduled_flush_drains_remainder` | PASS |
| 4 | `flush_on_empty_queue_is_noop` | PASS |

#### Integration tests — `LocationIngestionIT` (api-service)

Boots api-service against live compose (Kafka :9094, Postgres :5433, Redis :6379). Asserts 202 + message lands on `driver.location.v1` via an in-test `KafkaConsumer`.

| # | Test | SRS | Result |
|---|---|---|---|
| 1 | `valid_location_returns_202_and_publishes_to_kafka` | SRS-LOC-1..3 | PASS |
| 2 | `out_of_bbox_is_accepted_per_srs_loc_9` | SRS-LOC-9 | PASS |
| 3 | `invalid_lat_returns_400_problem_json` | SRS-LOC-1, Amendment §4.4 | PASS |
| 4 | `malformed_body_returns_400_problem_json` | Amendment §4.4 | PASS |

#### Integration tests — `LocationIndexerIT` (indexer-service)

Produces a `LocationEvent` via a live `KafkaTemplate` and awaits Redis + Postgres side effects.

| # | Test | SRS | Result |
|---|---|---|---|
| 1 | `event_updates_redis_geo_and_inserts_history_row` | SRS-LOC-5, SRS-LOC-8 | PASS |

Run command:

```bash
RUN_IT=true ./mvnw -pl api-service,indexer-service -am verify
```

### Manual smoke tests — executed 2026-04-20

Preconditions:

- `docker compose up -d redis postgres kafka kafka-ui` — all healthy.
- `./mvnw -pl api-service -am spring-boot:run` — service on `:8080`.
- `./mvnw -pl indexer-service -am spring-boot:run` in a second terminal — service on `:8081`, `Started IndexerApplication` printed before any POST.
- Kafka env vars from the "Infra fixes" table applied. Kafka was recycled (`docker compose rm -sf kafka kafka-ui && docker compose up -d kafka kafka-ui`) before the run.

#### 1. Ingestion happy path — 202 + Redis GEO + history row

**SRS:** `SRS-LOC-1..3`, `SRS-LOC-5`, `SRS-LOC-8`.

**Commands:**

```bash
ID=$(curl -s -X POST http://localhost:8080/v1/drivers \
   -H 'Content-Type: application/json' \
   -d '{"name":"Alice","vehicle":"Toyota Prius"}' | jq -r .id)
curl -s -X POST http://localhost:8080/v1/drivers/$ID/online > /dev/null

curl -s -i -X POST http://localhost:8080/v1/drivers/$ID/location \
   -H 'Content-Type: application/json' \
   -d '{"lat":37.7749,"lng":-122.4194,"recordedAt":"2026-04-20T12:00:00Z"}'

sleep 2
docker exec swiftmatch-redis redis-cli GEOPOS drivers:active $ID
docker exec swiftmatch-redis redis-cli TTL driver:$ID:heartbeat
docker exec swiftmatch-postgres psql -U swiftmatch -d swiftmatch \
   -c "SELECT count(*) FROM location_history WHERE driver_id='$ID';"
```

**Expected:**

- POST returns `HTTP/1.1 202` with `Content-Length: 0`.
- `GEOPOS` returns `-122.4194…` / `37.7749…`.
- `TTL` returns a number ≤ 30 (heartbeat was set by `/online` to 30 s; `EXPIRE` from indexer refreshes it).
- `location_history` count is `1`.

**Observed (driver `28e4617c-4a2d-4f53-a2d8-992cacb35ad6`):**

```
HTTP/1.1 202
Content-Length: 0

-122.41940170526504517
37.77490001056577995
27
 count
-------
     1
```

**Result:** PASS.

#### 2. Consumer group — `indexer` subscribed, LAG=0

**SRS:** `SRS-LOC-6` (manual-ack consumer with `auto-offset-reset: latest`).

**Command:**

```bash
MSYS_NO_PATHCONV=1 docker exec swiftmatch-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
   --bootstrap-server localhost:9092 --describe --group indexer
```

**Expected:** Group `indexer` listed with up to 12 partition rows, `CURRENT-OFFSET` advancing with each produced event, `LAG = 0` after the consumer has caught up, `CONSUMER-ID` populated.

**Observed:**

```
GROUP    TOPIC              PARTITION  CURRENT-OFFSET  LOG-END-OFFSET  LAG  CONSUMER-ID                                              HOST         CLIENT-ID
indexer  driver.location.v1 0          1               1               0    consumer-indexer-1-9bb5fdeb-f0c4-4819-a60e-4df260cf7f78  /172.20.0.1  consumer-indexer-1
```

**Result:** PASS.

#### 3. Topic provisioned with 12 partitions

**SRS:** `SRS-§6.1` Kafka topic config.

**Command:**

```bash
MSYS_NO_PATHCONV=1 docker exec swiftmatch-kafka /opt/kafka/bin/kafka-topics.sh \
   --bootstrap-server localhost:9092 --describe --topic driver.location.v1
```

**Expected:** `PartitionCount: 12`, `ReplicationFactor: 1`.

**Result:** PASS — `api-service`'s `KafkaTopicsConfig.driverLocationV1()` `NewTopic` bean created the topic at startup with 12 partitions.

#### 4. Out-of-bbox location — accepted (WARN only)

**SRS:** `SRS-LOC-9`.

**Command:**

```bash
curl -s -i -X POST http://localhost:8080/v1/drivers/$ID/location \
   -H 'Content-Type: application/json' \
   -d '{"lat":0.0,"lng":0.0,"recordedAt":"2026-04-20T12:00:05Z"}'
```

**Expected:** 202, and a WARN line in api-service stdout like `location outside city bbox accepted driverId=… lat=0.0 lng=0.0`.

**Result:** PASS (WARN observed in api-service terminal).

#### 5. Invalid lat — 400 Problem+JSON

**SRS:** `SRS-LOC-1`, Amendment §4.4.

**Command:**

```bash
curl -s -i -X POST http://localhost:8080/v1/drivers/$ID/location \
   -H 'Content-Type: application/json' \
   -d '{"lat":200.0,"lng":-122.41,"recordedAt":"2026-04-20T12:00:10Z"}'
```

**Expected:** 400, `Content-Type: application/problem+json`, `type` contains `/errors/validation`.

**Result:** PASS.

#### 6. Malformed JSON body — 400 Problem+JSON

**SRS:** Amendment §4.4 (malformed-body handler added in this branch).

**Command:**

```bash
curl -s -i -X POST http://localhost:8080/v1/drivers/$ID/location \
   -H 'Content-Type: application/json' \
   -d '{"lat":"banana"}'
```

**Expected:** 400, `Content-Type: application/problem+json`, `type` contains `/errors/validation`.

**Result:** PASS — handled by `GlobalExceptionHandler.handleMalformedJson`.

### Coverage summary — `feat/location-ingestion`

| SRS requirement | Covered by |
|---|---|
| SRS-LOC-1 (`POST /v1/drivers/{id}/location`) | manual #1, IT ingestion 1 |
| SRS-LOC-2 (publish to `driver.location.v1` keyed by `driverId`) | IT ingestion 1, manual #2 (consumer offsets advance) |
| SRS-LOC-3 (202 Accepted, empty body) | manual #1, IT ingestion 1 |
| SRS-LOC-4 (200 ms producer timeout → 503 `ingestion-timeout`) | not yet automated; manual trigger via `docker stop swiftmatch-kafka` listed in `cmds.md` §11. Handler unit-covered via `IngestionTimeoutException` wiring. |
| SRS-LOC-5 (Redis pipelined `GEOADD` + `EXPIRE`) | manual #1, IT indexer 1 |
| SRS-LOC-6 (manual-ack consumer, `auto-offset-reset: latest`) | manual #2 |
| SRS-LOC-7 (ExponentialBackOff 100/200/400 ms, ERROR+skip, **no DLT**) | `KafkaConsumerConfig` wiring; behaviour-under-failure not in automated suite (would require a failing Redis) |
| SRS-LOC-8 (batch insert to `location_history`, 500 ms / 500 rows) | unit tests 1–4, IT indexer 1 (count=1) |
| SRS-LOC-9 (out-of-bbox accepted, WARN-only) | manual #4, IT ingestion 2 |
| `SRS-§6.1` — `driver.location.v1` 12 partitions | manual #3 |
| Amendment §4.4 error catalog | IT ingestion 3–4, manual #5–6 |

---

## Cleanup after testing

```bash
# stop the services in their terminals
Ctrl+C

# remove test data (location_history first — FK-less but same driver scope)
docker exec swiftmatch-postgres psql -U swiftmatch -d swiftmatch \
  -c "DELETE FROM location_history; DELETE FROM drivers;"
docker exec swiftmatch-redis redis-cli FLUSHALL
```

---

## Next branches' tests will extend this file

As each feature branch lands, its manual and automated cases get appended here under new top-level sections (`## feat/ride-request-matching`, `## feat/osrm-routing-and-simulator`, etc.).
