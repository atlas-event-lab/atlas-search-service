-- ADR-0009: per-night hotel availability projection; fold flight availability into flight_projections;
-- drop the shared availability_projections. Truncate + rebuild from catalog/inventory events (impl
-- plan Phase 6), so this is forward-only DDL and does not migrate row data.

-- 1. Flight availability folded into the flight projection (absolute reserved + version guard).
ALTER TABLE flight_projections ADD COLUMN capacity INT    NOT NULL DEFAULT 0;
ALTER TABLE flight_projections ADD COLUMN reserved INT    NOT NULL DEFAULT 0;
ALTER TABLE flight_projections ADD COLUMN version  BIGINT NOT NULL DEFAULT 0;

-- 2. Per-night hotel availability projection (replaces the hotel side of availability_projections).
CREATE TABLE room_type_availability (
    id          UUID        NOT NULL,
    resource_id UUID        NOT NULL,          -- roomTypeId (Inventory rekey / partition key)
    stay_date   DATE        NOT NULL,
    capacity    INT         NOT NULL DEFAULT 0,
    reserved    INT         NOT NULL DEFAULT 0,
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version     BIGINT      NOT NULL DEFAULT 0,  -- monotonic guard for absolute reserved updates
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_room_type_availability PRIMARY KEY (id),
    CONSTRAINT uq_search_room_type_availability_night UNIQUE (resource_id, stay_date),
    CONSTRAINT chk_search_rta_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT chk_search_rta_capacity CHECK (capacity >= 0),
    CONSTRAINT chk_search_rta_reserved CHECK (reserved >= 0)
);

-- Night-range query filters/joins by (resource_id, stay_date); rolling job scans by stay_date.
CREATE INDEX idx_search_rta_resource_stay ON room_type_availability (resource_id, stay_date);
CREATE INDEX idx_search_rta_stay_date     ON room_type_availability (stay_date);

-- 3. The shared availability projection is superseded by the two above.
DROP TABLE IF EXISTS availability_projections;
