package com.atlas.search.search.repository;

import com.atlas.search.search.entity.TripOffer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface TripOfferRepository extends JpaRepository<TripOffer, UUID> {

    /**
     * Eagerly fetches items to avoid N+1 queries when resolving a trip detail.
     */
    @Query("SELECT t FROM TripOffer t LEFT JOIN FETCH t.items WHERE t.id = :id")
    Optional<TripOffer> findByIdWithItems(@Param("id") UUID id);

    /**
     * Deletes all expired TripOffer rows; items are removed by ON DELETE CASCADE.
     * Called by the idempotent TTL sweep scheduler.
     */
    @Modifying
    @Query("DELETE FROM TripOffer t WHERE t.expiresAt < :threshold")
    int deleteExpiredBefore(@Param("threshold") Instant threshold);
}
