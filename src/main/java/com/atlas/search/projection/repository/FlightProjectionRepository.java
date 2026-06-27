package com.atlas.search.projection.repository;

import com.atlas.search.projection.entity.FlightProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.UUID;

public interface FlightProjectionRepository extends JpaRepository<FlightProjection, UUID>,
    JpaSpecificationExecutor<FlightProjection> {

}
