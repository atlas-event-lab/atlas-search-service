package com.atlas.search.search.scheduler;

import com.atlas.search.search.repository.TripOfferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Idempotent scheduled sweep that hard-deletes expired TripOffer rows.
 * TripOfferItems are removed by ON DELETE CASCADE.
 * Running it multiple times or after a crash is safe (coding-standards §Spring Boot).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TripOfferSweep {

    private final TripOfferRepository tripOfferRepository;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${atlas.search.trip-offer.sweep-interval-ms:300000}")
    @Transactional
    public void sweep() {
        Instant threshold = Instant.now(clock);
        int deleted = tripOfferRepository.deleteExpiredBefore(threshold);
        if (deleted > 0) {
            log.info("TripOffer sweep removed {} expired offer(s)", deleted);
        }
    }
}
