package com.atlas.search.projection.service;

import com.atlas.search.config.HotelSearchProperties;
import com.atlas.search.projection.entity.RoomTypeNightAvailabilityProjection;
import com.atlas.search.projection.repository.RoomTypeNightAvailabilityProjectionRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rolling maintenance of the hotel availability projection (ADR-0009). As {@code today} advances it
 * creates the new far night (cloning the previous frontier, {@code reserved = 0}, {@code version = 0})
 * and purges nights past the retention window.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotelProjectionMaintenanceServiceImpl implements HotelProjectionMaintenanceService {

    private final RoomTypeNightAvailabilityProjectionRepository repository;
    private final HotelSearchProperties properties;
    private final Clock clock;

    @Override
    @Transactional
    public int rollHorizonForward() {
        LocalDate today = LocalDate.now(clock);
        LocalDate frontier = today.plusDays((long) properties.horizonDays() - 1);
        LocalDate source = frontier.minusDays(1);

        Set<UUID> alreadyExtended = repository.findByStayDate(frontier).stream()
                .map(RoomTypeNightAvailabilityProjection::getResourceId)
                .collect(Collectors.toSet());

        List<RoomTypeNightAvailabilityProjection> newNights = new ArrayList<>();
        for (RoomTypeNightAvailabilityProjection row : repository.findByStayDate(source)) {
            if (alreadyExtended.contains(row.getResourceId())) {
                continue;
            }
            newNights.add(new RoomTypeNightAvailabilityProjection(
                    UUID.randomUUID(), row.getResourceId(), frontier, row.getCapacity(), 0, row.getStatus(), 0));
        }
        if (!newNights.isEmpty()) {
            repository.saveAll(newNights);
            log.info("Rolled hotel projection forward: created {} night(s) for {}", newNights.size(), frontier);
        }
        return newNights.size();
    }

    @Override
    @Transactional
    public int purgePastNights() {
        LocalDate cutoff = LocalDate.now(clock).minusDays(properties.purgeAfterDays());
        int deleted = repository.deleteByStayDateBefore(cutoff);
        if (deleted > 0) {
            log.info("Purged {} completed hotel night(s) older than {}", deleted, cutoff);
        }
        return deleted;
    }
}
