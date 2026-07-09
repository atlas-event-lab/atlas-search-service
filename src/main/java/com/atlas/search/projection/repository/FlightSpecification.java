package com.atlas.search.projection.repository;

import com.atlas.search.projection.entity.FlightProjection;
import com.atlas.search.projection.entity.ProjectionStatus;
import com.atlas.search.search.dto.FlightSearchRequest;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
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

      //  AVAILABILITY FILTER — folded into FlightProjection (ADR-0009): available = capacity − reserved.
      Expression<Integer> availableExpr = cb.diff(root.get("capacity"), root.get("reserved"));
      predicates.add(cb.greaterThanOrEqualTo(availableExpr, paxRequiringSeat));

      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }
}