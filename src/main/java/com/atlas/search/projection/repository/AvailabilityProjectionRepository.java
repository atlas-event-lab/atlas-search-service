package com.atlas.search.projection.repository;

import com.atlas.search.projection.entity.AvailabilityProjection;
import com.atlas.search.projection.entity.ResourceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AvailabilityProjectionRepository extends JpaRepository<AvailabilityProjection, UUID> {

    Optional<AvailabilityProjection> findByResourceTypeAndResourceId(ResourceType resourceType, UUID resourceId);
}
