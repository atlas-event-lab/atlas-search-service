package com.atlas.search.projection.service;

/**
 * Rolling maintenance of the per-night hotel availability projection (ADR-0009). Mirrors Inventory's
 * rolling horizon so the read model keeps the same bookable window and purges long-past nights. Both
 * operations are idempotent so the scheduled job is safe to re-run.
 */
public interface HotelProjectionMaintenanceService {

    /** Clones the previous frontier night forward for every room type; returns rows created. */
    int rollHorizonForward();

    /** Purges completed nights older than the retention window; returns rows deleted. */
    int purgePastNights();
}
