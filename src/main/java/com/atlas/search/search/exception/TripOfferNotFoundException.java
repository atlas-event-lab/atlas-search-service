package com.atlas.search.search.exception;

import java.util.UUID;

public class TripOfferNotFoundException extends RuntimeException {

    public TripOfferNotFoundException(UUID tripId) {
        super("TripOffer not found: " + tripId);
    }
}
