package org.sopt.ssingserver.domain.matching.service;

import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferStatus;
import org.sopt.ssingserver.domain.matching.repository.MatchingOfferRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MatchingOfferExpirationTriggerService {

    private static final Logger log = LoggerFactory.getLogger(MatchingOfferExpirationTriggerService.class);
    private static final int OFFER_EXPIRATION_SCAN_BATCH_SIZE = 100;

    private final MatchingOfferRepository matchingOfferRepository;
    private final MatchingOfferExpirationService matchingOfferExpirationService;
    private final Clock clock;

    public void expireOverdueOffers() {
        List<Long> matchingOfferIds = matchingOfferRepository.findIdsByStatusAndExpiresAtLessThanEqualOrderByIdAsc(
                MatchingOfferStatus.OFFERED,
                clock.instant(),
                PageRequest.of(0, OFFER_EXPIRATION_SCAN_BATCH_SIZE)
        );

        int successCount = 0;
        int failureCount = 0;
        for (Long matchingOfferId : matchingOfferIds) {
            try {
                matchingOfferExpirationService.expireOffer(matchingOfferId);
                successCount++;
            } catch (RuntimeException exception) {
                failureCount++;
                logExpirationFailure(matchingOfferId, exception);
            }
        }

        logExpirationSummary(matchingOfferIds.size(), successCount, failureCount);
    }

    private void logExpirationFailure(Long matchingOfferId, RuntimeException exception) {
        log.atWarn()
                .addKeyValue("event", "matching.offer.expiration.failed")
                .addKeyValue("matching_offer_id", String.valueOf(matchingOfferId))
                .setCause(exception)
                .log("Matching offer expiration failed");
    }

    private void logExpirationSummary(int requestedCount, int successCount, int failureCount) {
        if (requestedCount == 0) {
            return;
        }

        log.atInfo()
                .addKeyValue("event", "matching.offer.expiration.batch.completed")
                .addKeyValue("requested_count", requestedCount)
                .addKeyValue("success_count", successCount)
                .addKeyValue("failure_count", failureCount)
                .log("Matching offer expiration batch completed");
    }
}
