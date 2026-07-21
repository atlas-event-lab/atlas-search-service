# Atlas — Search Service

> Read side of Atlas (CQRS): builds query-optimized flight/hotel projections from events.

Part of **[Atlas](https://github.com/atlas-event-lab)**. See the
[search CQRS diagrams](https://github.com/atlas-event-lab/atlas/tree/main/diagrams/search-cqrs.md).

## Responsibilities

- Maintain read-model projections for flight and hotel search, folding in catalog and
  per-night availability facts.
- Compute offers **per request** as live reads (no snapshot/offer table, no TTL).
- Terminal consumer: owns no business events and produces none.

## Tech

Java 21 · Spring Boot · Spring Data JPA · PostgreSQL (`search_db`) · Kafka · Keycloak JWT.

## API

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/search/flights` | Search flights (live offer computation) |
| GET | `/api/v1/search/hotels` | Search hotels (per-night availability) |

## Events

**Consumes:** `flight.created/updated/deleted`, `hotel.created/updated/deleted`,
`inventory.flight.reserved/released/expired`, `inventory.hotel.reserved/released/expired`.
**Produces:** none.

> Search does **not** consume `booking.*` or `payment.*` and holds no booking-history
> projection — booking history is served by the Booking service.

## Projections

`FlightProjection` (catalog + price + availability), `HotelProjection` (catalog + price),
`RoomTypeNightAvailabilityProjection` (per room-type × night). Rebuildable from events;
availability applied last-writer-wins per resource key via a `version` guard (ADR-0008/0009).

## Data

Owns `search_db` (database-per-service).

## Patterns

Idempotent consumers (`ConsumedEvent`) · event-sourced projections · read-model rebuild
(Exp 07).

## Run locally

```bash
docker compose up search-service
```

Env: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `KAFKA_BOOTSTRAP_SERVERS`, `KEYCLOAK_ISSUER_URI`.

## License

Apache-2.0 — see [`LICENSE`](./LICENSE).
