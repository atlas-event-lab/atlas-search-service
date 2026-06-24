package com.atlas.search.shared.messaging;

/**
 * Event types consumed by Search Service. Recorded on each {@code ConsumedEvent} row for
 * idempotency (EVT-005, EVT-008). Search consumes Flight/Hotel catalog events and Inventory
 * resource-facing availability events; it produces nothing.
 */
public enum ConsumerEventType {
    // Flight / Hotel catalog (owned by Flight / Hotel Service)
    FLIGHT_CREATED,
    FLIGHT_UPDATED,
    FLIGHT_DELETED,
    HOTEL_CREATED,
    HOTEL_UPDATED,
    HOTEL_DELETED,

    // Inventory resource-facing availability (owned by Inventory Service)
    INVENTORY_FLIGHT_RESERVED,
    INVENTORY_FLIGHT_RELEASED,
    INVENTORY_FLIGHT_EXPIRED,
    INVENTORY_HOTEL_RESERVED,
    INVENTORY_HOTEL_RELEASED,
    INVENTORY_HOTEL_EXPIRED
}
