package com.atlas.search.projection.repository;

import com.atlas.search.projection.entity.HotelProjection;
import com.atlas.search.projection.entity.HotelRoomType;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface HotelRoomTypeRepository
        extends JpaRepository<HotelProjection, UUID>, JpaSpecificationExecutor<HotelRoomType> {}
