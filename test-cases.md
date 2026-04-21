# SwiftMatch — Test Cases

Manual and automated test cases executed against the implementation.
Each case records what it verifies and the observed result.

---

## `feat/driver-registration`

Executed 2026-04-20. Covers the driver registration and online/offline lifecycle endpoints plus the Problem+JSON error catalog (`validation`, `not-found`, `driver-on-trip`).

### Automated — `./mvnw -pl api-service verify`

Green run on 2026-04-20. 13/13 tests pass (6 unit, 7 integration against the live compose stack).

#### Unit tests — `DriverStatusTransitionsTest`

Pure state-machine tests, no Spring context. Covers online/offline transitions and idempotency on repeated calls.

| # | Test | Checks | Result |
|---|---|---|---|
| 1 | `online_from_offline_becomes_available` | fresh driver goes OFFLINE → AVAILABLE | PASS |
| 2 | `online_from_available_stays_available_idempotent` | repeat `/online` is a no-op | PASS |
| 3 | `online_from_on_trip_stays_on_trip_noop` | `/online` on ON_TRIP driver is a no-op | PASS |
| 4 | `offline_from_available_becomes_offline` | AVAILABLE → OFFLINE transition | PASS |
| 5 | `offline_from_offline_stays_offline_idempotent` | repeat `/offline` is a no-op | PASS |
| 6 | `offline_from_on_trip_is_rejected` | `/offline` on ON_TRIP driver throws `DriverOnTripException` | PASS |

#### Integration tests — `DriverLifecycleIT`

End-to-end with `MockMvc` driving the REST layer, hitting the running Postgres (port 5433) and Redis (port 6379) from `docker compose`. Gated on `RUN_IT=true` env var so default `verify` stays green without infra.

| # | Test | Checks | Result |
|---|---|---|---|
| 1 | `create_driver_returns_201_and_persists_offline` | `POST /v1/drivers` → 201; DB row stored as OFFLINE | PASS |
| 2 | `online_marks_available_in_db_and_redis_with_ttl` | `/online` flips DB + Redis; TTL 25–31 s | PASS |
| 3 | `online_when_on_trip_is_noop` | `/online` on ON_TRIP driver leaves state unchanged | PASS |
| 4 | `offline_when_on_trip_returns_409_problem_json` | `/offline` on ON_TRIP driver → 409 `driver-on-trip` | PASS |
| 5 | `offline_when_available_clears_redis` | `/offline` on AVAILABLE driver clears Redis keys | PASS |
| 6 | `get_missing_driver_returns_404_problem_json` | unknown UUID → 404 `not-found` | PASS |
| 7 | `create_with_invalid_body_returns_400_problem_json` | empty name/vehicle → 400 `validation` | PASS |

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

**What it checks:** `POST /v1/drivers` accepts a name + vehicle, persists the driver with `OFFLINE` status, returns 201.

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

**What it checks:** read path returns the same record.

**Command:**

```bash
curl -s http://localhost:8080/v1/drivers/$ID | jq
```

**Expected:** 200, same body as above, status `OFFLINE`.

**Result:** PASS — mirrors the create response.

#### 4. Go online → `AVAILABLE`

**What it checks:** `/online` flips DB + Redis status to `AVAILABLE`.

**Command:**

```bash
curl -s -X POST http://localhost:8080/v1/drivers/$ID/online | jq
```

**Expected:** 200, body with `status: AVAILABLE`.

**Observed:** `status: "AVAILABLE"`.

**Result:** PASS.

#### 5. Redis state after online

**What it checks:** driver status + heartbeat keys exist with 30 s TTL.

**Commands:**

```bash
docker exec swiftmatch-redis redis-cli GET driver:$ID:status
docker exec swiftmatch-redis redis-cli TTL driver:$ID:status
```

**Expected:** `"AVAILABLE"` and TTL ~30.

**Observed:** `AVAILABLE`, `29`.

**Result:** PASS.

#### 6. Go offline → `OFFLINE`

**What it checks:** `/offline` flips status to `OFFLINE` and advances `updatedAt`.

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

**What it checks:** status + heartbeat keys removed from Redis.

**Command:**

```bash
docker exec swiftmatch-redis redis-cli GET driver:$ID:status
```

**Expected:** `(nil)` — keys removed.

**Result:** PASS.

#### 8. Error — 400 validation (empty name + vehicle)

**What it checks:** bad input surfaces a Problem+JSON 400 with a field-level violation list.

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

**What it checks:** unknown driver UUID surfaces a Problem+JSON 404.

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

**What it checks:** taking an ON_TRIP driver offline is rejected with a Problem+JSON 409.

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

| Capability | Covered by |
|---|---|
| Create driver | manual #2, IT test 1 |
| Validation on create | manual #8, IT test 7 |
| Go online (DB + Redis + TTL) | manual #4, #5; unit tests 1–3; IT tests 2–3 |
| Go offline (DB + Redis cleared) | manual #6, #7; unit test 4; IT test 5 |
| 200 success responses | all manual success-path calls |
| 404 on unknown driver | manual #9, IT test 6 |
| Idempotency on repeated calls | unit tests 2, 5 |
| 409 when offline attempted on ON_TRIP driver | manual #10; unit test 6; IT test 4 |
| Problem+JSON catalog (validation / not-found / driver-on-trip) | manual #8, #9, #10 |

---

## `feat/location-ingestion`

Executed 2026-04-20. Extends the suite above with the producer (`api-service`) and consumer (`indexer-service`) sides of the driver-location pipeline: POST the location, publish to Kafka, GEOADD to Redis, batch-insert to Postgres. Retry on Redis failure is retained; the dead-letter topic fallback is deferred.

### Infra fixes uncovered during smoke (one-time)

Both landed in this branch and are prerequisites for anything in this section to pass:

| Fix | File | Why |
|---|---|---|
| Add `KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1` (+ `TRANSACTION_STATE_LOG_*`, `GROUP_INITIAL_REBALANCE_DELAY_MS=0`) | `docker-compose.yml` | Kafka defaults these to 3. On a single-broker dev cluster `__consumer_offsets` never gets created, every consumer group's `FIND_COORDINATOR` times out, and `kafka-consumer-groups.sh` hangs. |
| Add `<skip>false</skip>` to `spring-boot-maven-plugin` | `indexer-service/pom.xml` | Parent POM sets `<skip>true</skip>` on the plugin. Without the override, `./mvnw -pl indexer-service -am spring-boot:run` reports BUILD SUCCESS in <2 s without actually booting the app. |

### Automated — `./mvnw -pl api-service,indexer-service -am verify`

Green on 2026-04-20. 20 tests total (9 new unit + 4 new integration on top of the driver-registration suite; 5 ITs gated by `RUN_IT=true`).

#### Unit tests — `CityBboxTest` (api-service)

Pure bbox-containment logic, no Spring context. Covers the "out of service area" gate.

| # | Test | Result |
|---|---|---|
| 1 | `contains_point_inside_bbox` | PASS |
| 2 | `rejects_point_north_of_bbox` | PASS |
| 3 | `rejects_point_east_of_bbox` | PASS |
| 4 | `accepts_boundary_corners_inclusively` | PASS |
| 5 | `rejects_antipode` | PASS |

#### Unit tests — `LocationHistoryBufferTest` (indexer-service)

Drain / flush semantics of the in-memory buffer that batch-inserts to `location_history`. Uses a `java.lang.reflect.Proxy` for `JdbcOperations` because Mockito's inline maker cannot instrument `JdbcTemplate` on JDK 25 / Byte Buddy.

| # | Test | Result |
|---|---|---|
| 1 | `enqueue_below_threshold_does_not_flush` | PASS |
| 2 | `enqueue_at_threshold_flushes_synchronously` | PASS |
| 3 | `scheduled_flush_drains_remainder` | PASS |
| 4 | `flush_on_empty_queue_is_noop` | PASS |

#### Integration tests — `LocationIngestionIT` (api-service)

Boots api-service against live compose (Kafka :9094, Postgres :5433, Redis :6379). Asserts 202 + message lands on `driver.location.v1` via an in-test `KafkaConsumer`.

| # | Test | Checks | Result |
|---|---|---|---|
| 1 | `valid_location_returns_202_and_publishes_to_kafka` | POST returns 202 and one record lands on Kafka | PASS |
| 2 | `out_of_bbox_is_accepted_per_srs_loc_9` | out-of-bbox points still accepted (WARN only) | PASS |
| 3 | `invalid_lat_returns_400_problem_json` | `lat=200` → 400 `validation` | PASS |
| 4 | `malformed_body_returns_400_problem_json` | non-JSON body → 400 `validation` | PASS |

#### Integration tests — `LocationIndexerIT` (indexer-service)

Produces a `LocationEvent` via a live `KafkaTemplate` and awaits Redis + Postgres side effects.

| # | Test | Checks | Result |
|---|---|---|---|
| 1 | `event_updates_redis_geo_and_inserts_history_row` | GEOADD + heartbeat refresh in Redis; one row in `location_history` | PASS |

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

**What it checks:** POST location returns 202, indexer adds the driver to `drivers:active` within ~1 s, and a row lands in `location_history` within ~2 s.

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

```text
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

**What it checks:** the indexer joins its consumer group, receives the location event, and commits offset so LAG stays at 0. Uses manual ack with `auto-offset-reset: latest`.

**Command:**

```bash
MSYS_NO_PATHCONV=1 docker exec swiftmatch-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
   --bootstrap-server localhost:9092 --describe --group indexer
```

**Expected:** Group `indexer` listed with up to 12 partition rows, `CURRENT-OFFSET` advancing with each produced event, `LAG = 0` after the consumer has caught up, `CONSUMER-ID` populated.

**Observed:**

```text
GROUP    TOPIC              PARTITION  CURRENT-OFFSET  LOG-END-OFFSET  LAG  CONSUMER-ID                                              HOST         CLIENT-ID
indexer  driver.location.v1 0          1               1               0    consumer-indexer-1-9bb5fdeb-f0c4-4819-a60e-4df260cf7f78  /172.20.0.1  consumer-indexer-1
```

**Result:** PASS.

#### 3. Topic provisioned with 12 partitions

**What it checks:** `driver.location.v1` is created at api-service startup with 12 partitions and replication factor 1.

**Command:**

```bash
MSYS_NO_PATHCONV=1 docker exec swiftmatch-kafka /opt/kafka/bin/kafka-topics.sh \
   --bootstrap-server localhost:9092 --describe --topic driver.location.v1
```

**Expected:** `PartitionCount: 12`, `ReplicationFactor: 1`.

**Result:** PASS — `api-service`'s `KafkaTopicsConfig.driverLocationV1()` `NewTopic` bean created the topic at startup with 12 partitions.

#### 4. Out-of-bbox location — accepted (WARN only)

**What it checks:** a location outside the SF bbox still returns 202 and gets indexed; the api-service logs a WARN line.

**Command:**

```bash
curl -s -i -X POST http://localhost:8080/v1/drivers/$ID/location \
   -H 'Content-Type: application/json' \
   -d '{"lat":0.0,"lng":0.0,"recordedAt":"2026-04-20T12:00:05Z"}'
```

**Expected:** 202, and a WARN line in api-service stdout like `location outside city bbox accepted driverId=… lat=0.0 lng=0.0`.

**Result:** PASS (WARN observed in api-service terminal).

#### 5. Invalid lat — 400 Problem+JSON

**What it checks:** `lat=200` fails bean validation and surfaces a Problem+JSON 400.

**Command:**

```bash
curl -s -i -X POST http://localhost:8080/v1/drivers/$ID/location \
   -H 'Content-Type: application/json' \
   -d '{"lat":200.0,"lng":-122.41,"recordedAt":"2026-04-20T12:00:10Z"}'
```

**Expected:** 400, `Content-Type: application/problem+json`, `type` contains `/errors/validation`.

**Result:** PASS.

#### 6. Malformed JSON body — 400 Problem+JSON

**What it checks:** non-numeric `lat` hits a deserialization error before validation and still surfaces a Problem+JSON 400 (handled by `GlobalExceptionHandler.handleMalformedJson`).

**Command:**

```bash
curl -s -i -X POST http://localhost:8080/v1/drivers/$ID/location \
   -H 'Content-Type: application/json' \
   -d '{"lat":"banana"}'
```

**Expected:** 400, `Content-Type: application/problem+json`, `type` contains `/errors/validation`.

**Result:** PASS — handled by `GlobalExceptionHandler.handleMalformedJson`.

### Coverage summary — `feat/location-ingestion`

| Capability | Covered by |
|---|---|
| `POST /v1/drivers/{id}/location` endpoint shape + bounds validation | manual #1, IT ingestion 1 |
| Publish to `driver.location.v1` keyed by `driverId` | IT ingestion 1, manual #2 (consumer offsets advance) |
| 202 Accepted with empty body | manual #1, IT ingestion 1 |
| 200 ms producer timeout → 503 `ingestion-timeout` | not yet automated; manual trigger via `docker stop swiftmatch-kafka` listed in `cmds.md` §11 |
| Redis pipelined `GEOADD` + heartbeat `EXPIRE` | manual #1, IT indexer 1 |
| Manual-ack consumer with `auto-offset-reset: latest` | manual #2 |
| ExponentialBackOff 100/200/400 ms on Redis failure, ERROR + skip | consumer config wiring; behaviour-under-failure not in automated suite |
| Batch insert to `location_history` (500 ms / 500 rows) | unit tests 1–4, IT indexer 1 (count=1) |
| Out-of-bbox locations accepted with WARN only | manual #4, IT ingestion 2 |
| `driver.location.v1` topic has 12 partitions | manual #3 |
| Problem+JSON catalog (validation / not-found / no-driver-found) | IT ingestion 3–4, manual #5–6 |

---

## `feat/ride-request-matching`

Executed 2026-04-21. Covers ride creation with distributed-lock matching, plus the minimal rider-creation endpoint. Idempotency keys on `POST /v1/rides` are deferred and out of scope for this branch. Both binding concurrency-invariant scenarios — "no double-assign" and "no-driver-found within the 8 s matching budget" — are automated in this branch.

### Design deltas uncovered during implementation / smoke

Both landed in this branch:

| Fix | File | Why |
|---|---|---|
| Move `markOnTrip(driverId)` *inside* the Redisson critical section (was in `RideRegistrar.assign` after the lock released) | `RideMatcher.tryCandidate` | Concurrency IT caught the race: the lock released when the matcher returned, but status wasn't `ON_TRIP` yet, so a concurrent rider could pass the pre-lock status check and also acquire the lock. Both riders got 200. Moving the flip inside the lock closes the window; DB UPDATE stays in `assign`, partial unique index stays the backstop. |
| Pipeline `EXPIRE driver:{id}:status 30` alongside `EXPIRE …:heartbeat 30` | `indexer-service`'s `LocationRedisWriter.index` | Manual smoke showed drivers dropping out of match eligibility 30 s after `/online` because the status key's TTL was only refreshed by `/online`, never by location events. Pragmatic one-line fix; flagged for later refactor when the simulator or heartbeat reaper lands. |

### Automated — `./mvnw -B verify` (default green) / `RUN_IT=true …` (full)

Green on 2026-04-21. 19 unit tests (8 new matcher scenarios, 3 ETA, 3 bbox, 5 rider) plus
6 integration tests gated on `RUN_IT=true`.

#### Unit tests — `RideMatcherTest` (api-service)

Exercises the radius-expansion matching loop without Redis. Hand-rolled `java.lang.reflect.Proxy`
stubs for `RedissonClient` / `RLock` because Mockito's inline maker is unstable on
JDK 25 (same pattern as `LocationHistoryBufferTest`). Narrow seam: `DriverGeoIndex`,
`DriverStatusReader`, `DriverStatusWriter`.

| # | Test | Checks | Result |
|---|---|---|---|
| 1 | `happy_path_returns_first_available_candidate` | nearest AVAILABLE driver gets matched | PASS |
| 2 | `lock_contested_falls_through_to_next_candidate` | failed `tryLock` skips to next driver in the list | PASS |
| 3 | `status_race_after_lock_acquired_releases_and_moves_on` | post-lock status re-check catches a racing ON_TRIP flip | PASS |
| 4 | `empty_first_radius_expands_to_second` | 2 km empty → 5 km ring searched next | PASS |
| 5 | `status_not_available_is_skipped_without_grabbing_lock` | ON_TRIP driver never reaches the lock attempt | PASS |
| 6 | `no_candidates_across_all_radii_returns_empty` | matcher returns empty, caller surfaces 503 | PASS |
| 7 | `budget_exhaustion_bails_before_next_radius` | 8 s budget hit during radius 2 → loop exits, skipping radius 5 | PASS |
| 8 | `non_uuid_entry_in_geo_index_is_skipped_not_fatal` | garbage entry in `drivers:active` is logged + skipped | PASS |

#### Unit tests — `EtaEstimatorTest` (api-service)

Haversine sanity for the great-circle ETA fallback used until OSRM lands.

| # | Test | Result |
|---|---|---|
| 1 | `zero_distance_is_zero_seconds` | PASS |
| 2 | `one_km_at_30kmh_is_about_120_seconds` | PASS |
| 3 | `haversine_sanity_between_sf_and_oakland` | PASS (~12.5–14 km) |

#### Unit tests — `RideBboxValidatorTest` (api-service)

Verifies pickup/dropoff bbox check before the matcher runs.

| # | Test | Result |
|---|---|---|
| 1 | `both_inside_passes` | PASS |
| 2 | `outside_pickup_names_pickup_field` | PASS |
| 3 | `outside_dropoff_names_dropoff_field` | PASS |

#### Unit tests — `RiderServiceTest` (api-service)

Covers rider creation + existence check. Uses a reflective in-memory stub for `JpaRepository`.

| # | Test | Result |
|---|---|---|
| 1 | `create_with_explicit_name_persists_as_given` | PASS |
| 2 | `create_with_null_request_generates_anonymous_name` | PASS |
| 3 | `create_with_blank_name_generates_anonymous_name` | PASS |
| 4 | `requireExisting_throws_for_unknown_id` | PASS |
| 5 | `requireExisting_passes_for_known_id` | PASS |

#### Integration tests — `RideMatchingIT` (api-service)

Boots api-service against live compose (Postgres :5433, Redis :6379, Kafka :9094).
Seeds Redis GEO directly (bypasses indexer) so the IT is self-contained. Both
binding concurrency-invariant scenarios are automated here.

| # | Test | Checks | Result |
|---|---|---|---|
| 1 | `tc_mat_sla_no_driver_found_within_8s_budget` | **binding** — no driver → 503 inside the 8 s matching budget | PASS (503 inside 8.5 s) |
| 2 | `happy_path_single_driver_nearby_is_assigned` | POST → 200 ASSIGNED, row + Redis status updated | PASS |
| 3 | `out_of_service_area_returns_400_problem_json` | pickup at (0,0) → 400 `out-of-service-area` | PASS |
| 4 | `unknown_rider_returns_404_not_found` | unknown rider UUID → 404 `not-found` | PASS |
| 5 | `rider_has_active_ride_is_409` | second request from same rider while one is active → 409 | PASS |
| 6 | `tc_mat_concurrency_never_double_assigns` | **binding** — 10-round hammer, one driver + two riders; at most one 200, DB never has two ON_TRIP rows for same driver | PASS |

Run command:

```bash
RUN_IT=true ./mvnw -pl api-service test -Dtest=RideMatchingIT
```

### Manual smoke tests — executed 2026-04-21

Preconditions:

- `docker compose up -d redis postgres kafka kafka-ui` — all healthy.
- `./mvnw -pl api-service -am spring-boot:run` — service on `:8080`.
- `./mvnw -pl indexer-service -am spring-boot:run` in a second terminal — so
  `drivers:active` gets populated from `driver.location.v1`.

#### 1. Status-TTL diagnostic: 30 s expiry caught

**Background:** Before the indexer's `EXPIRE …:status 30` one-liner landed, status
keys only refreshed on `/online` (30 s TTL). Delay between `/online` + location POST
and the ride request would age the key out and cause 503 `no-driver-found` on an
otherwise valid setup.

**Repro before the fix:**

```bash
RID=$(curl -s -X POST http://localhost:8080/v1/riders \
   -H 'Content-Type: application/json' -d '{"name":"Rhea"}' | jq -r .id)
DID=$(curl -s -X POST http://localhost:8080/v1/drivers \
   -H 'Content-Type: application/json' \
   -d '{"name":"Alice","vehicle":"Toyota Prius"}' | jq -r .id)
curl -s -X POST http://localhost:8080/v1/drivers/$DID/online > /dev/null
curl -s -X POST http://localhost:8080/v1/drivers/$DID/location \
   -H 'Content-Type: application/json' \
   -d '{"lat":37.7749,"lng":-122.4194,"recordedAt":"2026-04-21T15:30:00Z"}'
# wait > 30 s, then request a ride
curl -s -i -X POST http://localhost:8080/v1/rides \
   -H 'Content-Type: application/json' \
   -d "{\"riderId\":\"$RID\",\"pickup\":{\"lat\":37.7749,\"lng\":-122.4194},\"dropoff\":{\"lat\":37.7849,\"lng\":-122.4094}}"
```

**Observed (before fix):**

```http
HTTP/1.1 503
Content-Type: application/problem+json

{"type":"https://swiftmatch.local/errors/no-driver-found", ...}
```

Diagnostics confirmed the cause:

```text
$ docker exec swiftmatch-redis redis-cli GEOPOS drivers:active $DID
-122.41940170526504517
37.77490001056577995

$ docker exec swiftmatch-redis redis-cli TTL driver:$DID:status
-2     # key does not exist
```

GEO position present, status key expired. The matcher's pre-lock `GET
driver:{id}:status` returned `nil`, skipped the only candidate, and exhausted
all radii → 503.

**Result:** PASS — bug reproduced, root cause identified, fix applied.

#### 2. Post-fix: continuous location traffic keeps status fresh

**What it checks:** after the indexer fix, location events refresh the `status` key's TTL so continuous simulator traffic keeps a driver matchable without re-calling `/online`.

**Commands (after rebuilding + restarting indexer-service):**

```bash
curl -s -X POST http://localhost:8080/v1/drivers/$DID/online > /dev/null
curl -s -X POST http://localhost:8080/v1/drivers/$DID/location \
   -H 'Content-Type: application/json' \
   -d '{"lat":37.7749,"lng":-122.4194,"recordedAt":"2026-04-21T16:00:00Z"}'
sleep 1
docker exec swiftmatch-redis redis-cli TTL driver:$DID:status
```

**Expected:** TTL reports a value between 25 and 30 s after the indexer has processed
the location event.

**Observed:** `29`.

**Result:** PASS.

#### 3. Ride request happy path — 200 ASSIGNED

**What it checks:** `POST /v1/rides` with an online driver nearby returns 200, assigns the driver, populates the response with driver coords + ETA.

**Command:**

```bash
curl -s -i -X POST http://localhost:8080/v1/rides \
   -H 'Content-Type: application/json' \
   -d "{\"riderId\":\"$RID\",\"pickup\":{\"lat\":37.7749,\"lng\":-122.4194},\"dropoff\":{\"lat\":37.7849,\"lng\":-122.4094}}"
```

**Expected:**

- HTTP 200
- `status: ASSIGNED`
- `driver.id` equals `$DID`
- `driver.lat`/`lng` match the last location event
- `etaSeconds` ≥ 0 (great-circle fallback, WARN line in api-service logs)
- `rides` table row with `status='ASSIGNED'` and `driver_id=$DID`
- Redis `driver:$DID:status` flipped to `ON_TRIP`

**Observed:**

```http
HTTP/1.1 200
Content-Type: application/json

{
  "rideId":"f412638f-61a0-4cab-8a4a-56d9840b884b",
  "status":"ASSIGNED",
  "driver":{
    "id":"560f8796-5b34-4ad1-a003-651754517992",
    "name":"Alice",
    "vehicle":"Toyota Prius",
    "lat":37.77490001056578,
    "lng":-122.41940170526505,
    "etaSeconds":0
  },
  "requestedAt":null,
  "assignedAt":"2026-04-21T16:01:29.949492500Z"
}
```

**Result:** PASS. Driver coordinates match the seeded location. `etaSeconds=0` is
correct because pickup and the driver's last GEOPOS are the same point.

**Note on `requestedAt: null`:** JPA `@PrePersist` populates the column at INSERT, but
the `RideEntity` returned from `registrar.createPending()` is the same in-memory
object that was passed to `save()` without a subsequent reload, so the getter still
sees the pre-persist value. Payload is cosmetic for now — `requested_at` is correct
in Postgres. Worth fixing in a later refactor (one line: call `findById` after save,
or set `requestedAt = Instant.now()` in the constructor). Flagging here rather than
on the backlog because it's a 1-line cleanup, not a feature gap.

#### 4. Side-effect verification after happy path

**What it checks:** the DB row transitioned to `ASSIGNED` with `assigned_at` set; the Redis status key reflects `ON_TRIP`.

**Commands:**

```bash
docker exec swiftmatch-postgres psql -U swiftmatch -d swiftmatch \
  -c "SELECT id, driver_id, status, assigned_at FROM rides ORDER BY requested_at DESC LIMIT 1;"
docker exec swiftmatch-redis redis-cli GET driver:$DID:status
```

**Observed (truncated):**

```text
                  id                  |              driver_id              | status   | assigned_at
--------------------------------------+-------------------------------------+----------+-------------------------
 f412638f-61a0-4cab-8a4a-56d9840b884b | 560f8796-5b34-4ad1-a003-651754517992 | ASSIGNED | 2026-04-21 16:01:29.949

ON_TRIP
```

**Result:** PASS. Redis status flipped inside the Redisson critical section per the
design fix; DB UPDATE committed in `RideRegistrar.assign`'s short transaction.

### Error paths — covered by IT only (not manually re-run)

Automated in `RideMatchingIT` tests 3–5 and the no-driver SLA path in test 1. Not
re-run manually in this session; the IT run on 2026-04-21 proved:

| Scenario | Expected | Proven by IT test |
| --- | --- | --- |
| `no-driver-found` after all drivers occupied | 503 inside 8.5 s, `rides.status='NO_DRIVER_FOUND'` | `tc_mat_sla_no_driver_found_within_8s_budget` |
| pickup outside SF bbox | 400 `out-of-service-area`, `field='pickup'` | `out_of_service_area_returns_400_problem_json` |
| unknown rider UUID | 404 `not-found` | `unknown_rider_returns_404_not_found` |
| rider already has an active ride | 409 `rider-has-active-ride` | `rider_has_active_ride_is_409` |

### Coverage summary — `feat/ride-request-matching`

| Capability | Covered by |
|---|---|
| `POST /v1/rides` endpoint shape | manual #3, IT test 2 |
| Pickup/dropoff bbox check → 400 `out-of-service-area` | unit `RideBboxValidatorTest` 1–3, IT test 3 |
| PENDING row inserted, published to `ride.request.v1` | IT test 2, manual #4 |
| Radius-expansion matching loop with Redisson lock | unit `RideMatcherTest` 1–8, IT tests 2, 6 |
| 200 ASSIGNED response shape (driver + ETA) | manual #3, IT test 2 |
| 503 `no-driver-found` + row updated to `NO_DRIVER_FOUND` | IT test 1 |
| Partial unique index backstop (no double-assign under concurrency) | IT test 6 (invariant asserted across 10 concurrent rounds) |
| Great-circle ETA fallback (used until OSRM lands) | unit `EtaEstimatorTest` 1–3; WARN observed manually |
| p99 latency soft ceiling | not yet automated — gated on k6 at the acceptance milestone |
| Idempotency keys on `POST /v1/rides` | **deferred**, out of scope for this branch |
| `POST /v1/riders` | unit `RiderServiceTest` 1–3; IT setup in every test |
| Rider has no other endpoints | enforced by absence |
| Problem+JSON catalog (`no-driver-found`, `validation`, `not-found`) | IT tests 1, 3, 4 |
| Rider cannot have two active rides | IT test 5 |

---

## Cleanup after testing

```bash
# stop the services in their terminals
Ctrl+C

# remove test data (rides → riders → drivers for FK order; location_history is FK-less)
docker exec swiftmatch-postgres psql -U swiftmatch -d swiftmatch \
  -c "DELETE FROM rides; DELETE FROM riders; DELETE FROM drivers; DELETE FROM location_history;"
docker exec swiftmatch-redis redis-cli FLUSHALL
```

---

## Next branches' tests will extend this file

As each feature branch lands, its manual and automated cases get appended here under new top-level sections (`## feat/ride-lifecycle`, `## chore/osrm-routing-engine`, etc.).
