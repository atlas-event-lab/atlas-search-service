-- ADR-0002: TripOffer snapshot is removed. Offers are now computed per request
-- as live reads of the index projections (no persistence, no TTL).
-- trip_offer_items has ON DELETE CASCADE from trip_offers.

DROP TABLE IF EXISTS trip_offer_items;
DROP TABLE IF EXISTS trip_offers;
