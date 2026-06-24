package com.atlas.search.projection.repository;

import com.atlas.search.projection.entity.HotelProjection;
import com.atlas.search.projection.entity.ProjectionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface HotelProjectionRepository extends JpaRepository<HotelProjection, UUID> {

    /**
     * Finds active hotels in a city with at least the minimum rating.
     * Eagerly fetches room types to avoid N+1 queries during offer assembly.
     */
    @Query("""
            SELECT DISTINCT h FROM HotelProjection h
            LEFT JOIN FETCH h.roomTypes rt
            WHERE h.city = :city
              AND h.status = :status
              AND h.rating >= :minRating
            """)
    List<HotelProjection> findActiveInCityWithRating(
            @Param("city") String city,
            @Param("status") ProjectionStatus status,
            @Param("minRating") int minRating);
}
