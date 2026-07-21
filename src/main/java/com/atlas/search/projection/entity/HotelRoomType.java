package com.atlas.search.projection.entity;

import com.atlas.search.projection.dto.ImageDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * A room type belonging to a {@link HotelProjection}.
 * {@code roomTypeId} is the source UUID from Hotel Service and is used as
 * the {@code resourceId} in the AvailabilityProjection (read_model.md).
 */
@Entity
@Table(name = "hotel_room_types")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class HotelRoomType {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hotel_id", nullable = false, updatable = false)
    private HotelProjection hotel;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "price_per_night", nullable = false, precision = 19, scale = 2)
    private BigDecimal pricePerNight;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "max_occupancy", nullable = false)
    private int maxOccupancy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "images", nullable = false, columnDefinition = "jsonb")
    private List<ImageDto> images;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
