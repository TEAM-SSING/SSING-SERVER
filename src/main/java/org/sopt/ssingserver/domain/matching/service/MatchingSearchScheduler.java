package org.sopt.ssingserver.domain.matching.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// SEARCHING으로 보이는 REQUESTED 요청의 주기적 재탐색 스케줄러
@Component
@ConditionalOnProperty(name = "ssing.matching.search-scheduler.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class MatchingSearchScheduler {

    private static final Logger log = LoggerFactory.getLogger(MatchingSearchScheduler.class);

    private final MatchingOfferExpirationTriggerService matchingOfferExpirationTriggerService;
    private final MatchingSearchTriggerService matchingSearchTriggerService;

    // REQUESTED 요청을 주기적으로 재탐색한다. 무기한 정책에서는 제안 만료 트리거가 no-op으로 동작한다.
    @Scheduled(fixedDelay = 60_000)
    public void runScheduledSearch() {
        try {
            matchingOfferExpirationTriggerService.expireOverdueOffers();
        } catch (RuntimeException exception) {
            log.atWarn()
                    .addKeyValue("event", "matching.scheduler.offer.expiration.failed")
                    .setCause(exception)
                    .log("Matching offer expiration scheduler step failed");
        }

        try {
            matchingSearchTriggerService.triggerAllRequested();
        } catch (RuntimeException exception) {
            log.atWarn()
                    .addKeyValue("event", "matching.scheduler.search.trigger.failed")
                    .setCause(exception)
                    .log("Matching search scheduler step failed");
        }
    }
}
