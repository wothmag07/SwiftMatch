-- Location history table per [SRS-§6.1] and [SRS-LOC-8].
-- Partition scheme follows the SRS literally (range on recorded_at). Daily rotation
-- is deferred (tracked as BL-9 in specs/BACKLOG.md); for MVP we provision a single
-- partition covering the demo window.

CREATE TABLE location_history (
    id          bigserial,
    driver_id   uuid             NOT NULL,
    lat         double precision NOT NULL,
    lng         double precision NOT NULL,
    recorded_at timestamptz      NOT NULL,
    PRIMARY KEY (id, recorded_at)
) PARTITION BY RANGE (recorded_at);

CREATE TABLE location_history_p_2026
    PARTITION OF location_history
    FOR VALUES FROM ('2026-01-01') TO ('2027-01-01');

CREATE INDEX location_history_driver_recorded_at
    ON location_history (driver_id, recorded_at DESC);
