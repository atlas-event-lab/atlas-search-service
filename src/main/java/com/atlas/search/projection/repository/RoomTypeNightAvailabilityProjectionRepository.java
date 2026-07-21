package com.atlas.search.projection.repository;

import com.atlas.search.projection.entity.RoomTypeNightAvailabilityProjection;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for the per-night hotel availability projection (ADR-0009). Accesses only local
 * projections (ARCH-003).
 */
public interface RoomTypeNightAvailabilityProjectionRepository
        extends JpaRepository<RoomTypeNightAvailabilityProjection, UUID> {

    Optional<RoomTypeNightAvailabilityProjection> findByResourceIdAndStayDate(UUID resourceId, LocalDate stayDate);

    /** Future nights of a set of room types (catalog materialize/reconcile/disable). */
    List<RoomTypeNightAvailabilityProjection> findByResourceIdInAndStayDateGreaterThanEqual(
            Collection<UUID> resourceIds, LocalDate from);

    /** All rows on a given night (rolling job clones the frontier forward). */
    List<RoomTypeNightAvailabilityProjection> findByStayDate(LocalDate stayDate);

    /** Purges completed nights older than {@code cutoff} (rolling job). */
    int deleteByStayDateBefore(LocalDate cutoff);
}
