package com.atlas.search.projection.repository;

import com.atlas.search.projection.entity.FlightProjection;
import com.atlas.search.projection.entity.ProjectionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface FlightProjectionRepository extends JpaRepository<FlightProjection, UUID> {

    /**
     * Finds active flights matching origin, destination, and departure date (UTC).
     * Uses date cast on departure_time to match the calendar date independent of time.
     */
    @Query("""
            SELECT f FROM FlightProjection f
            WHERE f.origin = :origin
              AND f.destination = :destination
              AND CAST(f.departureTime AS LocalDate) = :departureDate
              AND f.status = :status
            """)
    List<FlightProjection> findByOriginDestinationAndDate(
            @Param("origin") String origin,
            @Param("destination") String destination,
            @Param("departureDate") LocalDate departureDate,
            @Param("status") ProjectionStatus status);
}
