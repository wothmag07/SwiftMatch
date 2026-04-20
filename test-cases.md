# SwiftMatch — Test Cases

Manual and automated test cases executed against the implementation.
Each case cites the SRS requirement it proves and records the observed result.

---

## Automated — `./mvnw -pl api-service verify`

Green run on 2026-04-20. 13/13 tests pass (6 unit, 7 integration against the live compose stack).

### Unit tests — `DriverStatusTransitionsTest`

Pure state-machine tests, no Spring context. Traces `SRS-DRV-5..10` and `SRS-DRV-9` (idempotency).

| # | Test | SRS | Result |
|---|---|---|---|
| 1 | `online_from_offline_becomes_available` | SRS-DRV-5 | PASS |
| 2 | `online_from_available_stays_available_idempotent` | SRS-DRV-9 | PASS |
| 3 | `online_from_on_trip_stays_on_trip_noop` | SRS-DRV-5 | PASS |
| 4 | `offline_from_available_becomes_offline` | SRS-DRV-6 | PASS |
| 5 | `offline_from_offline_stays_offline_idempotent` | SRS-DRV-9 | PASS |
| 6 | `offline_from_on_trip_is_rejected` | SRS-DRV-10 | PASS (throws `DriverOnTripException`) |

### Integration tests — `DriverLifecycleIT`

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

---

## Manual smoke tests — executed 2026-04-20

Preconditions:

- `docker compose up -d redis postgres` — both healthy.
- `./mvnw -pl api-service -am spring-boot:run` — service on `:8080`.
- Second terminal in Git Bash.

### 1. Health check

```bash
curl http://localhost:8080/actuator/health
```

**Expected:** `{"status":"UP"}`

**Result:** PASS — service reports UP with Postgres + Redis subsystems healthy.

### 2. Create driver → `OFFLINE`

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

### 3. Fetch driver — `GET /v1/drivers/{id}`

**SRS:** `GET /v1/drivers/{id}` read path.

**Command:**

```bash
curl -s http://localhost:8080/v1/drivers/$ID | jq
```

**Expected:** 200, same body as above, status `OFFLINE`.

**Result:** PASS — mirrors the create response.

### 4. Go online → `AVAILABLE`

**SRS:** `SRS-DRV-5`

**Command:**

```bash
curl -s -X POST http://localhost:8080/v1/drivers/$ID/online | jq
```

**Expected:** 200, body with `status: AVAILABLE`.

**Observed:** `status: "AVAILABLE"`.

**Result:** PASS.

### 5. Redis state after online

**SRS:** `SRS-DRV-5` — driver status + heartbeat keys with 30 s TTL.

**Commands:**

```bash
docker exec swiftmatch-redis redis-cli GET driver:$ID:status
docker exec swiftmatch-redis redis-cli TTL driver:$ID:status
```

**Expected:** `"AVAILABLE"` and TTL ~30.

**Observed:** `AVAILABLE`, `29`.

**Result:** PASS.

### 6. Go offline → `OFFLINE`

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

### 7. Redis cleared on offline

**SRS:** `SRS-DRV-6`

**Command:**

```bash
docker exec swiftmatch-redis redis-cli GET driver:$ID:status
```

**Expected:** `(nil)` — keys removed.

**Result:** PASS.

### 8. Error — 400 validation (empty name + vehicle)

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

### 9. Error — 404 not-found

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

### 10. Error — 409 driver-on-trip

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

## Coverage summary

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

## Cleanup after testing

```bash
# stop the service in its terminal
Ctrl+C

# remove test data
docker exec swiftmatch-postgres psql -U swiftmatch -d swiftmatch -c "DELETE FROM drivers;"
```

---

## Next branches' tests will extend this file

As each feature branch lands, its manual and automated cases get appended here under new top-level sections (`## feat/location-ingestion`, `## feat/ride-request-matching`, etc.).
