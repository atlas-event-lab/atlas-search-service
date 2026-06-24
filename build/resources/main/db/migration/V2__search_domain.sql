-- ── Index projections ──────────────────────────────────────────────────────────
-- Built exclusively from Kafka events; rebuildable by replaying from topic start.
-- PK column: id (DB-006). Cross-aggregate refs keep the <entity>_id form.

CREATE TABLE flight_projections (
    id                UUID         NOT NULL,
    airline           VARCHAR(100) NOT NULL,
    origin            VARCHAR(10)  NOT NULL,
    destination       VARCHAR(10)  NOT NULL,
    departure_time    TIMESTAMPTZ  NOT NULL,
    arrival_time      TIMESTAMPTZ  NOT NULL,
    duration_minutes  INT          NOT NULL,
    -- stops is populated when Flight Service publishes FlightSegment count.
    -- Until dep. A1 is resolved, this column remains 0 (filter is inert).
    stops             INT          NOT NULL DEFAULT 0,
    base_price        NUMERIC(19,2) NOT NULL,
    currency          VARCHAR(3)   NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_flight_projections PRIMARY KEY (id),
    CONSTRAINT chk_flight_status CHECK (status IN ('ACTIVE', 'WITHDRAWN'))
);

CREATE INDEX idx_flight_proj_search ON flight_projections (origin, destination, DATE(departure_time AT TIME ZONE 'UTC'));
CREATE INDEX idx_flight_proj_status ON flight_projections (status);
CREATE INDEX idx_flight_proj_airline ON flight_projections (airline);

-- ── Hotels ──────────────────────────────────────────────────────────────────
CREATE TABLE hotel_projections (
    id         UUID         NOT NULL,
    name       VARCHAR(200) NOT NULL,
    city       VARCHAR(100) NOT NULL,
    country    VARCHAR(100) NOT NULL,
    rating     INT          NOT NULL,
    status     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_hotel_projections PRIMARY KEY (id),
    CONSTRAINT chk_hotel_status CHECK (status IN ('ACTIVE', 'WITHDRAWN')),
    CONSTRAINT chk_hotel_rating CHECK (rating BETWEEN 1 AND 5)
);

CREATE INDEX idx_hotel_proj_city_status ON hotel_projections (city, status);
CREATE INDEX idx_hotel_proj_rating      ON hotel_projections (rating);

-- Room types live inside hotel_projection (one-to-many).
-- room_type_id = source UUID from Hotel Service (used as resourceId in availability).
CREATE TABLE hotel_room_types (
    id               UUID          NOT NULL,
    hotel_id         UUID          NOT NULL,
    room_type_id     UUID          NOT NULL,
    name             VARCHAR(100)  NOT NULL,
    price_per_night  NUMERIC(19,2) NOT NULL,
    currency         VARCHAR(3)    NOT NULL,
    max_occupancy    INT           NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_hotel_room_types PRIMARY KEY (id),
    CONSTRAINT fk_hrt_hotel FOREIGN KEY (hotel_id) REFERENCES hotel_projections (id) ON DELETE CASCADE,
    CONSTRAINT uq_hrt_room_type_id UNIQUE (room_type_id)
);

CREATE INDEX idx_hrt_hotel_id    ON hotel_room_types (hotel_id);
CREATE INDEX idx_hrt_room_type   ON hotel_room_types (room_type_id);

-- ── Availability projection ──────────────────────────────────────────────────
-- One row per reservable resource (flight or hotel room type).
-- available = capacity - reserved (never negative; enforced by Inventory before publishing).
CREATE TABLE availability_projections (
    id            UUID        NOT NULL,
    resource_type VARCHAR(10) NOT NULL,
    resource_id   UUID        NOT NULL,
    capacity      INT         NOT NULL DEFAULT 0,
    reserved      INT         NOT NULL DEFAULT 0,
    status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_availability_projections PRIMARY KEY (id),
    CONSTRAINT uq_avail_resource UNIQUE (resource_type, resource_id),
    CONSTRAINT chk_avail_resource_type CHECK (resource_type IN ('FLIGHT', 'HOTEL')),
    CONSTRAINT chk_avail_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT chk_avail_capacity CHECK (capacity >= 0),
    CONSTRAINT chk_avail_reserved CHECK (reserved >= 0)
);

CREATE INDEX idx_avail_resource ON availability_projections (resource_type, resource_id);
CREATE INDEX idx_avail_status   ON availability_projections (status);

-- ── TripOffer (snapshot — NOT event-sourced, exempt from rebuild) ────────────
-- One row per returned offer. Identified by tripId; expires after TTL (15 min).
-- search_criteria stores the originating request as JSON for reproducibility.
CREATE TABLE trip_offers (
    id               UUID          NOT NULL,
    search_id        UUID          NOT NULL,
    search_criteria  TEXT          NOT NULL,
    total_amount     NUMERIC(19,2) NOT NULL,
    currency         VARCHAR(3)    NOT NULL,
    flight_count     INT           NOT NULL DEFAULT 1,
    hotel_count      INT           NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    expires_at       TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_trip_offers PRIMARY KEY (id)
);

CREATE INDEX idx_trip_offers_search_id  ON trip_offers (search_id);
CREATE INDEX idx_trip_offers_expires_at ON trip_offers (expires_at);

-- TripOfferItems: the bookable line items of each offer (≥1 FLIGHT, 0..1 HOTEL).
CREATE TABLE trip_offer_items (
    id                    UUID          NOT NULL,
    trip_offer_id         UUID          NOT NULL,
    item_type             VARCHAR(10)   NOT NULL,
    resource_id           UUID          NOT NULL,
    quantity              INT           NOT NULL,
    unit_price_amount     NUMERIC(19,2) NOT NULL,
    unit_price_currency   VARCHAR(3)    NOT NULL,
    line_total_amount     NUMERIC(19,2) NOT NULL,
    line_total_currency   VARCHAR(3)    NOT NULL,
    -- denormalized display — flight fields
    airline               VARCHAR(100),
    departure_time        TIMESTAMPTZ,
    arrival_time          TIMESTAMPTZ,
    stops                 INT,
    -- denormalized display — hotel fields
    hotel_name            VARCHAR(200),
    rating                INT,
    nights                INT,
    CONSTRAINT pk_trip_offer_items PRIMARY KEY (id),
    CONSTRAINT fk_toi_trip_offer FOREIGN KEY (trip_offer_id) REFERENCES trip_offers (id) ON DELETE CASCADE,
    CONSTRAINT chk_toi_type CHECK (item_type IN ('FLIGHT', 'HOTEL'))
);

CREATE INDEX idx_toi_trip_offer_id ON trip_offer_items (trip_offer_id);
