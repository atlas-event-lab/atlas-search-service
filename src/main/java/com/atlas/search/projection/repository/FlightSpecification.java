package com.atlas.search.projection.repository;

import com.atlas.search.projection.entity.AvailabilityProjection;
import com.atlas.search.projection.entity.FlightProjection;
import com.atlas.search.projection.entity.ProjectionStatus;
import com.atlas.search.projection.entity.ResourceType;
import com.atlas.search.search.dto.FlightSearchRequest;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import org.springframework.data.jpa.domain.Specification;

@UtilityClass
public class FlightSpecification {

  public static Specification<FlightProjection> withCriteria(FlightSearchRequest criteria) {
    return (root, query, cb) -> {

      Integer paxRequiringSeat = criteria.getAdults() + criteria.getChildren();

      List<Predicate> predicates = new ArrayList<>();

      predicates.add(cb.equal(root.get("origin"), criteria.getOrigin().toUpperCase()));
      predicates.add(cb.equal(root.get("destination"), criteria.getDestination().toUpperCase()));
      predicates.add(cb.equal(root.get("status"), ProjectionStatus.ACTIVE));

      Instant start = criteria.getDepartureDate()
          .atStartOfDay(ZoneOffset.UTC)
          .toInstant();

      Instant end = criteria.getDepartureDate()
          .plusDays(1)
          .atStartOfDay(ZoneOffset.UTC)
          .toInstant();

      predicates.add(cb.between(root.get("departureTime"), start, end));

      // airlines
      if (criteria.getAirlines() != null && !criteria.getAirlines().isEmpty()) {
        predicates.add(root.get("airline").in(criteria.getAirlines()));
      }

      //  PRICE FILTER
      if (criteria.getMinPrice() != null) {
        predicates.add(cb.greaterThanOrEqualTo(
            root.get("basePrice"),
            criteria.getMinPrice()
        ));
      }

      if (criteria.getMaxPrice() != null) {
        predicates.add(cb.lessThanOrEqualTo(
            root.get("basePrice"),
            criteria.getMaxPrice()
        ));
      }

      //  AVAILABILITY FILTER (SUBQUERY)
      Subquery<Integer> subquery = query.subquery(Integer.class);
      Root<AvailabilityProjection> availRoot = subquery.from(AvailabilityProjection.class);

      Expression<Integer> availableExpr = cb.diff(
          availRoot.get("capacity"),
          availRoot.get("reserved")
      );

      subquery.select(availableExpr)
          .where(
              cb.equal(availRoot.get("resourceId"), root.get("id")),
              cb.equal(availRoot.get("resourceType"), ResourceType.FLIGHT),
              cb.equal(availRoot.get("status"), AvailabilityProjection.AvailabilityStatus.ACTIVE)
          );

      // available >= paxRequired
      predicates.add(cb.greaterThanOrEqualTo(subquery, paxRequiringSeat));

      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }
}