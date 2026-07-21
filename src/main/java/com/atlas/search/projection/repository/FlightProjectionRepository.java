package com.atlas.search.projection.repository;

import com.atlas.search.projection.entity.FlightProjection;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface FlightProjectionRepository
        extends JpaRepository<FlightProjection, UUID>, JpaSpecificationExecutor<FlightProjection> {}
