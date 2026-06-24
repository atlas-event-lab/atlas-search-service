package com.atlas.search.search.exception;

import java.util.UUID;

public class TripOfferExpiredException extends RuntimeException {

    public TripOfferExpiredException(UUID tripId) {
        super("TripOffer has expired; please search again: " + tripId);
    }
}
