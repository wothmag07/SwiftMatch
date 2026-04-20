-- SwiftMatch initial schema per [SRS-§6.1].
-- Rider/ride tables are provisioned here (empty for now); populated by feat/ride-request-matching.
-- location_history lands in V2 via feat/location-ingestion.

CREATE TABLE drivers (
    id          uuid PRIMARY KEY,
    name        varchar(120) NOT NULL,
    phone       varchar(20),
    vehicle     varchar(60)  NOT NULL,
    status      varchar(16)  NOT NULL DEFAULT 'OFFLINE'
                CHECK (status IN ('OFFLINE', 'AVAILABLE', 'ON_TRIP')),
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE riders (
    id          uuid PRIMARY KEY,
    name        varchar(120) NOT NULL,
    phone       varchar(20),
    created_at  timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE rides (
    id              uuid PRIMARY KEY,
    rider_id        uuid NOT NULL REFERENCES riders(id),
    driver_id       uuid REFERENCES drivers(id),
    status          varchar(20) NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING', 'ASSIGNED', 'ON_TRIP', 'COMPLETED',
                                      'CANCELLED', 'NO_DRIVER_FOUND', 'FAILED')),
    pickup_lat      double precision NOT NULL,
    pickup_lng      double precision NOT NULL,
    dropoff_lat     double precision NOT NULL,
    dropoff_lng     double precision NOT NULL,
    requested_at    timestamptz NOT NULL DEFAULT now(),
    assigned_at     timestamptz,
    started_at      timestamptz,
    completed_at    timestamptz,
    failure_reason  varchar(80)
);

-- Hard invariant backstop: at most one active trip per driver (see [SRS-MAT-7]).
-- Redisson lock is the fast path; this index is the authoritative safety net.
CREATE UNIQUE INDEX rides_one_active_per_driver
    ON rides (driver_id)
    WHERE status = 'ON_TRIP';

CREATE INDEX rides_rider_requested_at
    ON rides (rider_id, requested_at DESC);
