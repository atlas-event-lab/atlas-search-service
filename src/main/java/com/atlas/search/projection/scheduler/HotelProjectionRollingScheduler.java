package com.atlas.search.projection.scheduler;

import com.atlas.search.projection.service.HotelProjectionMaintenanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Rolls the per-night hotel availability projection forward and purges long-past nights (ADR-0009),
 * mirroring Inventory's rolling horizon. Idempotent and stateless (ARCH-010): {@code fixedDelay}
 * prevents overlap and both operations are safe to re-run. Runs daily by default because "today"
 * advances once a day.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HotelProjectionRollingScheduler {

    private final HotelProjectionMaintenanceService maintenanceService;

    @Scheduled(fixedDelayString = "${atlas.search.hotel.roll-interval-ms:86400000}",
            initialDelayString = "${atlas.search.hotel.roll-initial-delay-ms:60000}")
    public void rollAndPurge() {
        try {
            maintenanceService.rollHorizonForward();
            maintenanceService.purgePastNights();
        } catch (Exception e) {
            log.error("Hotel projection rolling/purge failed; will retry on the next tick", e);
        }
    }
}
