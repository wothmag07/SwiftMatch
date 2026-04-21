# SwiftMatch

Real-time driver-dispatch reference implementation — **Kafka + Redis + Spring Boot + OSRM**, simulating Uber-style matching on real San Francisco streets.

A portfolio project focused on the hard parts of ride matching: distributed locking under concurrency, geospatial search, low-latency pipelines, and honest test coverage of the invariants that matter.

---

## Status

Backend matching core is live. UI, simulator, and road-network routing still to come.

| Capability | State |
| --- | --- |
| Driver registration + online/offline lifecycle | • |
| Location ingestion pipeline (Kafka → Redis GEO + Postgres history) | • |
| Ride request + distributed-lock matching (radius expansion, 8 s SLA) | • |
| Rider creation endpoint | • |
| Ride lifecycle transitions (start / complete / cancel) | ^ next |
| OSRM road-network routing + ETA | ^ |
| Driver simulator (fake drivers following SF roads) | ^ |
| SSE fan-out to browser | ^ |
| React UI — rider + driver routes | ^ |
| k6 load tests at 100 drivers / 10 concurrent riders | ^ |

---

## Architecture

```text
Rider → HTTP
         ▼
  api-service :8080 ────► Postgres :5433   (drivers, riders, rides, location_history)
         │  produces
         ▼
  Kafka :9094 (host) / :9092 (container)
    topics:  driver.location.v1   (12 partitions, key = driverId)
             ride.request.v1      ( 6 partitions, key = riderId)
             ride.assignment.v1   ( 6 partitions, key = rideId)
         │
         ▼
  indexer-service :8081
    consumes driver.location.v1
    → Redis GEO set drivers:active   (used by the matcher)
    → batch-insert location_history  (analytics / replay)
```

**How matching works.** When a rider requests a ride, `api-service` runs a radius-expansion loop (2 km → 5 km → 10 km, 8 s total budget) against Redis `GEOSEARCH` on `drivers:active`. For each candidate it checks the `AVAILABLE` status key, grabs a Redisson distributed lock keyed on the driver (`lock:driver:{id}`), re-checks status under the lock, flips status to `ON_TRIP` inside the critical section, and writes the `ASSIGNED` row to Postgres. A partial unique index on `rides(driver_id) WHERE status='ON_TRIP'` is the last-line safety net — no driver can ever appear on two concurrent trips, even if the lock fails.

---

## Quickstart

Requires Docker Desktop, JDK 17, and Git Bash (on Windows) or any POSIX shell. The Maven Wrapper is checked in, so no separate Maven install is needed.

```bash
# 1. start the infrastructure (Redis, Postgres, Kafka, Kafka UI)
docker compose up -d redis postgres kafka kafka-ui

# 2. build everything
./mvnw -B verify

# 3. run the services (two terminals)
./mvnw -pl api-service -am spring-boot:run
./mvnw -pl indexer-service -am spring-boot:run
```

Endpoints / UIs once running:

| Service | URL |
| --- | --- |
| api-service health | <http://localhost:8080/actuator/health> |
| api-service metrics | <http://localhost:8080/actuator/prometheus> |
| Kafka UI | <http://localhost:8090> |
| Postgres | `localhost:5433` (`swiftmatch` / `swiftmatch` / `swiftmatch`) |
| Redis | `localhost:6379` |

Postgres runs on host port **5433** to avoid collisions with a locally installed Postgres (the container still listens on 5432 internally).

---

## Try it end-to-end

Assumes the quickstart is up.

```bash
# create a rider
RID=$(curl -s -X POST http://localhost:8080/v1/riders \
   -H 'Content-Type: application/json' \
   -d '{"name":"Rhea"}' | jq -r .id)

# create a driver, bring online, send one location inside SF
DID=$(curl -s -X POST http://localhost:8080/v1/drivers \
   -H 'Content-Type: application/json' \
   -d '{"name":"Alice","vehicle":"Toyota Prius"}' | jq -r .id)
curl -s -X POST http://localhost:8080/v1/drivers/$DID/online
curl -s -X POST http://localhost:8080/v1/drivers/$DID/location \
   -H 'Content-Type: application/json' \
   -d '{"lat":37.7749,"lng":-122.4194,"recordedAt":"2026-04-21T12:00:00Z"}'
sleep 1

# request a ride → 200 ASSIGNED with the driver attached
curl -s -X POST http://localhost:8080/v1/rides \
   -H 'Content-Type: application/json' \
   -d "{\"riderId\":\"$RID\",\"pickup\":{\"lat\":37.7749,\"lng\":-122.4194},\"dropoff\":{\"lat\":37.7849,\"lng\":-122.4094}}" \
   | jq
```

Driver coordinates in the response come from Redis GEO; the `rides` row is durably `ASSIGNED` in Postgres; a `ride.assignment.v1` record is on Kafka. Detailed smoke tests per feature live in [test-cases.md](test-cases.md).

---

## REST API (current)

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/v1/drivers` | Create a driver (returns with `OFFLINE` status) |
| `GET` | `/v1/drivers/{id}` | Fetch a driver |
| `POST` | `/v1/drivers/{id}/online` | Mark driver `AVAILABLE`, Redis status + heartbeat (30 s TTL) |
| `POST` | `/v1/drivers/{id}/offline` | Mark driver `OFFLINE`, clear Redis keys |
| `POST` | `/v1/drivers/{id}/location` | Accept a location update (returns 202, publishes to Kafka) |
| `POST` | `/v1/riders` | Create an anonymous rider |
| `POST` | `/v1/rides` | Request a ride — runs the matching loop, returns `ASSIGNED` driver or `503 no-driver-found` |

Errors are RFC 7807 Problem+JSON (`application/problem+json`). Stable error types on the MVP:

- `validation` (400)
- `not-found` (404)
- `no-driver-found` (503)

Others surface appropriate 4xx/5xx with non-binding `type` URIs (e.g. `out-of-service-area`, `driver-on-trip`, `rider-has-active-ride`).

---

## Testing

Default build stays green without any running infra:

```bash
./mvnw -B verify
```

Integration tests need the compose stack up and opt in via an env var:

```bash
RUN_IT=true ./mvnw -B verify
```

Two concurrency-invariant scenarios are automated and hammered repeatedly:

- **No-driver-found SLA** — no drivers online → 503 inside 8 s.
- **No double-assign under contention** — 10 rounds of two riders racing for one driver; the `rides` table never has two active rides pointing at the same driver.

Full per-feature test matrices and observed outputs live in [test-cases.md](test-cases.md).

---

## Project layout

```text
swiftmatch/
├── common/              # shared DTOs + Kafka event records
├── api-service/         # REST + Kafka producer + matching       :8080
├── indexer-service/     # Kafka → Redis GEO + Postgres history   :8081
├── stream-service/      # SSE fan-out to browser (upcoming)      :8084
├── simulator-service/   # Fake drivers on SF roads (upcoming)    :8085
├── docker-compose.yml   # Redis + Postgres + Kafka + Kafka UI (+ OSRM when M4 lands)
└── test-cases.md        # per-feature automated + manual test records
```

`cmds.md` at the repo root is a running log of every shell command used during development — build, infra, git, smoke tests, troubleshooting.

---

## Notes on the environment

- Developed on Windows with Git Bash. Prefer `./mvnw` over `.\mvnw.cmd` for consistency across platforms.
- Kafka runs in KRaft mode (no ZooKeeper) with single-broker overrides for `__consumer_offsets` and transaction-state replication factor — plain single-broker defaults would hang consumer groups.
- Mockito's inline maker is unstable on JDK 25 + Byte Buddy current; a few unit tests use `java.lang.reflect.Proxy` hand-rolled stubs instead. Not pretty, but deterministic.

---

## License

MIT — see [LICENSE](LICENSE).

> Not production-ready: no authentication, no TLS, local-only demo. Built as a portfolio reference, not a service.
