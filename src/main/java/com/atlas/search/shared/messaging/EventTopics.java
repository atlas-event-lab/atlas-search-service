package com.atlas.search.shared.messaging;

/**
 * Kafka topic name constants consumed by Search Service (topics.md).
 * Names verified against the producing services' EventTopics constants (2026-06-22).
 * Search produces nothing — it is a terminal consumer.
 */
public final class EventTopics {

    // ── Flight Service (owned by Flight Service) ──────────────────────────────
    public static final String FLIGHT_CREATED = "flight.created";
    public static final String FLIGHT_UPDATED = "flight.updated";
    public static final String FLIGHT_DELETED = "flight.deleted";

    // ── Hotel Service (owned by Hotel Service) ────────────────────────────────
    public static final String HOTEL_CREATED = "hotel.created";
    public static final String HOTEL_UPDATED = "hotel.updated";
    public static final String HOTEL_DELETED = "hotel.deleted";

    // ── Inventory resource-facing (owned by Inventory Service) ─────────────────
    public static final String INVENTORY_FLIGHT_RESERVED = "inventory.flight.reserved";
    public static final String INVENTORY_FLIGHT_RELEASED = "inventory.flight.released";
    public static final String INVENTORY_FLIGHT_EXPIRED  = "inventory.flight.expired";
    public static final String INVENTORY_HOTEL_RESERVED  = "inventory.hotel.reserved";
    public static final String INVENTORY_HOTEL_RELEASED  = "inventory.hotel.released";
    public static final String INVENTORY_HOTEL_EXPIRED   = "inventory.hotel.expired";

    private EventTopics() {}
}
