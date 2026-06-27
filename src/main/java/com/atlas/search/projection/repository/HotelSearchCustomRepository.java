package com.atlas.search.projection.repository;

import com.atlas.search.projection.dto.ImageDto;
import com.atlas.search.projection.entity.AvailabilityProjection;
import com.atlas.search.projection.entity.HotelProjection;
import com.atlas.search.projection.entity.HotelRoomType;
import com.atlas.search.projection.entity.ProjectionStatus;
import com.atlas.search.projection.entity.ResourceType;
import com.atlas.search.projection.repository.model.HotelRoomResult;
import com.atlas.search.search.dto.HotelSearchRequest;
import com.atlas.search.search.dto.HotelSearchRequest.HotelSortOption;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class HotelSearchCustomRepository {

  private final EntityManager em;
  private final ObjectMapper objectMapper;
  private static final String SORT_BY_RATING = "rating";
  private static final String SORT_BY_PRICE = "pricePerNight";

  public Page<HotelRoomResult> search(HotelSearchRequest criteria, Pageable pageable) {

    CriteriaBuilder cb = em.getCriteriaBuilder();

    CriteriaQuery<Tuple> cq = cb.createTupleQuery();

    Root<HotelRoomType> room = cq.from(HotelRoomType.class);
    Join<HotelRoomType, HotelProjection> hotel = room.join("hotel");

    Root<AvailabilityProjection> avail = cq.from(AvailabilityProjection.class);

    Expression<Integer> availableExpr =
        cb.diff(avail.get("capacity"), avail.get("reserved"));

    List<Predicate> predicates = new ArrayList<>();

    // joins
    predicates.add(cb.equal(avail.get("resourceId"), room.get("id")));
    predicates.add(cb.equal(avail.get("resourceType"), ResourceType.HOTEL));
    predicates.add(cb.equal(avail.get("status"), AvailabilityProjection.AvailabilityStatus.ACTIVE));

    predicates.add(cb.equal(hotel.get("city"), criteria.getCity()));
    predicates.add(cb.equal(hotel.get("status"), ProjectionStatus.ACTIVE));

    int occupancyPerRoom =
        (criteria.getGuests() + criteria.getRooms() - 1) / criteria.getRooms();

    predicates.add(cb.greaterThanOrEqualTo(
        room.get("maxOccupancy"),
        occupancyPerRoom
    ));

    predicates.add(cb.greaterThanOrEqualTo(
        availableExpr,
        criteria.getRooms()
    ));

    // dynamic filters
    if (criteria.getHotelRating() != null) {
      predicates.add(cb.greaterThanOrEqualTo(
          hotel.get("rating"),
          criteria.getHotelRating()
      ));
    }

    if (criteria.getMinPrice() != null) {
      predicates.add(cb.greaterThanOrEqualTo(
          room.get("pricePerNight"),
          criteria.getMinPrice()
      ));
    }

    if (criteria.getMaxPrice() != null) {
      predicates.add(cb.lessThanOrEqualTo(
          room.get("pricePerNight"),
          criteria.getMaxPrice()
      ));
    }

    cq.multiselect(
        hotel.get("id"),
        hotel.get("name"),
        hotel.get("city"),
        hotel.get("country"),
        hotel.get("rating"),
        room.get("id"),
        room.get("name"),
        room.get("maxOccupancy"),
        room.get("pricePerNight"),
        room.get("currency"),
        availableExpr,
        hotel.get("amenities"),
        hotel.get("images"),
        room.get("images")
    );

    cq.where(predicates.toArray(new Predicate[0]));

    // sorting
    if (convertSortingTypes(criteria.getSort()).equals(SORT_BY_RATING)) {
      cq.orderBy(cb.asc(hotel.get(convertSortingTypes(criteria.getSort()))));
    } else {
      cq.orderBy(cb.asc(room.get(convertSortingTypes(criteria.getSort()))));
    }

    TypedQuery<Tuple> query = em.createQuery(cq);

    query.setFirstResult((int) pageable.getOffset());
    query.setMaxResults(pageable.getPageSize());

    List<HotelRoomResult> content = query.getResultList().stream()
        .map(tuple -> new HotelRoomResult(
            tuple.get(0, UUID.class),
            tuple.get(1, String.class),
            tuple.get(2, String.class),
            tuple.get(3, String.class),
            tuple.get(4, Integer.class),
            tuple.get(5, UUID.class),
            tuple.get(6, String.class),
            tuple.get(7, Integer.class),
            tuple.get(8, BigDecimal.class),
            tuple.get(9, String.class),
            tuple.get(10, Integer.class),
            safeStringList(tuple.get(11)),
            safeImageList(tuple.get(12)),
            safeImageList(tuple.get(13))
        ))
        .toList();

    long total = content.size();

    return new PageImpl<>(content, pageable, total);
  }

  private String convertSortingTypes(HotelSortOption sortOption) {
    return switch (sortOption) {
      case HotelSortOption.PRICE -> SORT_BY_PRICE;
      case HotelSortOption.RATING -> SORT_BY_RATING;
    };
  }

  private List<String> safeStringList(Object rawList) {
    return ((List<?>) rawList).stream().map(String::valueOf).toList();
  }

  private List<ImageDto> safeImageList(Object rawList) {
    return ((List<?>) rawList)
        .stream()
        .map(e -> objectMapper.convertValue(e, ImageDto.class))
        .toList();
  }
}
