# SwiftMatch

Real-time driver dispatch reference implementation — Kafka + Redis + Spring Boot + React + OSRM.
Simulates Uber-style matching on real San Francisco streets.

**Status:** Pre-M1 scaffold.

## Docs

- [plan.md](plan.md) — original project brief
- [specs/prd.md](specs/prd.md) — Product Requirements
- [specs/sow.md](specs/sow.md) — Statement of Work
- [specs/srs.md](specs/srs.md) — Software Requirements Specification
- [specs/amendments/001-lite-mode.md](specs/amendments/001-lite-mode.md) — **active amendment**
- [BACKLOG.md](BACKLOG.md) — deferred features
- [PROGRESS.md](PROGRESS.md) — weekly log

## Quickstart

```bash
# infra: Kafka, Redis, Postgres, Kafka UI
docker compose up -d redis postgres kafka kafka-ui

# build all modules
mvn -B verify
```

- Kafka UI: <http://localhost:8090>
- Redis: `localhost:6379`
- Postgres: `localhost:5433` (swiftmatch / swiftmatch / swiftmatch) — host port is 5433 to avoid collision with any local Postgres install; container still listens on 5432 internally
- Kafka (from host): `localhost:9094`
- Kafka (container network): `kafka:9092`

## Project layout

```text
swiftmatch/
├── common/              # shared DTOs, event schemas
├── api-service/         # REST endpoints + Kafka producer      :8080
├── indexer-service/     # Kafka -> Redis GEO + Postgres        :8081
├── stream-service/      # SSE fan-out to browser               :8084
├── simulator-service/   # Fake drivers on SF roads via OSRM    :8085
├── web/                 # React + Vite + Leaflet (M5)          :5173
├── infra/osrm/          # OSRM preprocessing pipeline (M4)
└── specs/               # PRD, SoW, SRS, amendments
```

## License

MIT — see [LICENSE](LICENSE).

> Not production-ready: no authentication, no TLS, local-only demo.
