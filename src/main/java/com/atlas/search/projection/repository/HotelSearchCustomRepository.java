package com.atlas.search.projection.repository;

import com.atlas.search.projection.entity.HotelRoomType;
import com.atlas.search.projection.repository.model.HotelRoomResult;
import com.atlas.search.search.dto.HotelSearchRequest;
import com.atlas.search.search.dto.HotelSearchRequest.HotelSortOption;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

/**
 * Date-aware hotel availability query (ADR-0009). A room type is eligible iff <b>every</b> night of
 * the stay {@code [checkIn, checkOut)} exists in the calendar, is {@code ACTIVE}, and has
 * {@code available ≥ rooms} — expressed as {@code COUNT(rows in range) = nights} and
 * {@code COUNT(available ≥ rooms) = nights}. The eligible room type ids (with the range's minimum
 * availability) are found first, then the display rows are loaded — this avoids grouping by JSON
 * columns while keeping rating/price/maxOccupancy/sort/pagination.
 */
@Repository
@RequiredArgsConstructor
public class HotelSearchCustomRepository {

  private final EntityManager em;

  public Page<HotelRoomResult> search(HotelSearchRequest criteria, Pageable pageable) {
    int nights = (int) ChronoUnit.DAYS.between(criteria.getCheckIn(), criteria.getCheckOut());
    int occupancyPerRoom = (criteria.getGuests() + criteria.getRooms() - 1) / criteria.getRooms();

    Map<String, Object> params = new HashMap<>();
    String whereClause = buildWhere(criteria, nights, occupancyPerRoom, params);

    long total = count(whereClause, params);
    if (total == 0) {
      return new PageImpl<>(List.of(), pageable, 0);
    }

    // Ordered, paginated eligible room type ids with the range-minimum availability.
    String idSql = """
        SELECT a.resource_id AS room_type_id, MIN(a.capacity - a.reserved) AS min_avail
        FROM room_type_availability a
        JOIN hotel_room_types rt ON rt.id = a.resource_id
        JOIN hotel_projections h ON h.id = rt.hotel_id
        """ + whereClause + """
         GROUP BY a.resource_id, rt.price_per_night, h.rating
         HAVING COUNT(*) = :nights
            AND SUM(CASE WHEN (a.capacity - a.reserved) >= :rooms THEN 1 ELSE 0 END) = :nights
        """ + orderByClause(criteria.getSort());

    Query idQuery = em.createNativeQuery(idSql);
    params.forEach(idQuery::setParameter);
    idQuery.setFirstResult((int) pageable.getOffset());
    idQuery.setMaxResults(pageable.getPageSize());

    @SuppressWarnings("unchecked")
    List<Object[]> rows = idQuery.getResultList();

    // Preserve query order and remember each room type's range-minimum availability.
    List<UUID> orderedIds = new ArrayList<>();
    Map<UUID, Integer> availableById = new LinkedHashMap<>();
    for (Object[] row : rows) {
      UUID roomTypeId = toUuid(row[0]);
      orderedIds.add(roomTypeId);
      availableById.put(roomTypeId, ((Number) row[1]).intValue());
    }

    Map<UUID, HotelRoomType> roomsById = loadRoomTypes(orderedIds);

    List<HotelRoomResult> content = orderedIds.stream()
        .map(roomsById::get)
        .filter(java.util.Objects::nonNull)
        .map(rt -> toResult(rt, availableById.get(rt.getId())))
        .toList();

    return new PageImpl<>(content, pageable, total);
  }

  private String buildWhere(HotelSearchRequest c, int nights, int occupancyPerRoom, Map<String, Object> params) {
    StringBuilder where = new StringBuilder("""
        WHERE a.status = 'ACTIVE'
          AND a.stay_date >= :checkIn AND a.stay_date < :checkOut
          AND h.city = :city
          AND h.status = 'ACTIVE'
          AND rt.max_occupancy >= :occupancy
        """);
    params.put("checkIn", c.getCheckIn());
    params.put("checkOut", c.getCheckOut());
    params.put("city", c.getCity());
    params.put("occupancy", occupancyPerRoom);
    params.put("nights", nights);
    params.put("rooms", c.getRooms());

    if (c.getHotelRating() != null) {
      where.append(" AND h.rating >= :rating");
      params.put("rating", c.getHotelRating());
    }
    if (c.getMinPrice() != null) {
      where.append(" AND rt.price_per_night >= :minPrice");
      params.put("minPrice", c.getMinPrice());
    }
    if (c.getMaxPrice() != null) {
      where.append(" AND rt.price_per_night <= :maxPrice");
      params.put("maxPrice", c.getMaxPrice());
    }
    return where.toString();
  }

  private long count(String whereClause, Map<String, Object> params) {
    String countSql = """
        SELECT COUNT(*) FROM (
          SELECT a.resource_id
          FROM room_type_availability a
          JOIN hotel_room_types rt ON rt.id = a.resource_id
          JOIN hotel_projections h ON h.id = rt.hotel_id
        """ + whereClause + """
           GROUP BY a.resource_id
           HAVING COUNT(*) = :nights
              AND SUM(CASE WHEN (a.capacity - a.reserved) >= :rooms THEN 1 ELSE 0 END) = :nights
        ) eligible
        """;
    Query countQuery = em.createNativeQuery(countSql);
    params.forEach(countQuery::setParameter);
    return ((Number) countQuery.getSingleResult()).longValue();
  }

  private String orderByClause(HotelSortOption sort) {
    return switch (sort == null ? HotelSortOption.PRICE : sort) {
      case PRICE -> " ORDER BY rt.price_per_night ASC";
      case RATING -> " ORDER BY h.rating ASC";
    };
  }

  private Map<UUID, HotelRoomType> loadRoomTypes(List<UUID> ids) {
    if (ids.isEmpty()) {
      return Map.of();
    }
    List<HotelRoomType> rooms = em.createQuery(
            "SELECT rt FROM HotelRoomType rt JOIN FETCH rt.hotel WHERE rt.id IN :ids", HotelRoomType.class)
        .setParameter("ids", ids)
        .getResultList();
    Map<UUID, HotelRoomType> byId = new HashMap<>();
    rooms.forEach(rt -> byId.put(rt.getId(), rt));
    return byId;
  }

  private HotelRoomResult toResult(HotelRoomType rt, Integer available) {
    var hotel = rt.getHotel();
    return new HotelRoomResult(
        hotel.getId(),
        hotel.getName(),
        hotel.getCity(),
        hotel.getCountry(),
        hotel.getRating(),
        rt.getId(),
        rt.getName(),
        rt.getMaxOccupancy(),
        rt.getPricePerNight(),
        rt.getCurrency(),
        available == null ? 0 : available,
        hotel.getAmenities(),
        hotel.getImages(),
        rt.getImages());
  }

  private UUID toUuid(Object raw) {
    return (raw instanceof UUID uuid) ? uuid : UUID.fromString(raw.toString());
  }
}
