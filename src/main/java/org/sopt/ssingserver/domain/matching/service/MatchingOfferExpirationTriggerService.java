package org.sopt.ssingserver.domain.matching.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
    private static final String JOB_NAME = "matching-offer-expiration";
    // TODO: 만료 대기 제안이 계속 100개를 넘으면 backlog metric 추가 후 배치 크기 증대나 다중 페이지 처리로 확장한다.
    private static final int OFFER_EXPIRATION_SCAN_BATCH_SIZE = 100;

    private final MatchingOfferRepository matchingOfferRepository;
    private final MatchingOfferExpirationService matchingOfferExpirationService;
    private final Clock clock;

    public void expireOverdueOffers() {
        Instant startedAt = clock.instant();
        int requestedCount = 0;
        int successCount = 0;
        int failureCount = 0;

        logExpirationBatchStarted();

        try {
            List<Long> matchingOfferIds = matchingOfferRepository.findIdsByStatusAndExpiresAtLessThanEqualOrderByIdAsc(
                    MatchingOfferStatus.OFFERED,
                    clock.instant(),
                    PageRequest.of(0, OFFER_EXPIRATION_SCAN_BATCH_SIZE)
            );

            requestedCount = matchingOfferIds.size();
            for (Long matchingOfferId : matchingOfferIds) {
                try {
                    matchingOfferExpirationService.expireOffer(matchingOfferId);
                    successCount++;
                } catch (RuntimeException exception) {
                    failureCount++;
                    logExpirationFailure(matchingOfferId, exception);
                }
            }

            logExpirationSummary(requestedCount, successCount, failureCount, startedAt);
        } catch (RuntimeException exception) {
            logExpirationBatchFailure(requestedCount, successCount, failureCount, startedAt, exception);
        }
    }

    private void logExpirationBatchStarted() {
        log.atInfo()
                .addKeyValue("event", "matching.offer.expiration.batch.started")
                .addKeyValue("job_name", JOB_NAME)
                .log("Matching offer expiration batch started");
    }

    private void logExpirationFailure(Long matchingOfferId, RuntimeException exception) {
        log.atWarn()
                .addKeyValue("event", "matching.offer.expiration.failed")
                .addKeyValue("job_name", JOB_NAME)
                .addKeyValue("matching_offer_id", String.valueOf(matchingOfferId))
                .setCause(exception)
                .log("Matching offer expiration failed");
    }

    private void logExpirationSummary(
            int requestedCount,
            int successCount,
            int failureCount,
            Instant startedAt
    ) {
        log.atInfo()
                .addKeyValue("event", "matching.offer.expiration.batch.completed")
                .addKeyValue("job_name", JOB_NAME)
                .addKeyValue("status", "completed")
                .addKeyValue("duration_ms", elapsedMillis(startedAt))
                .addKeyValue("requested_count", requestedCount)
                .addKeyValue("success_count", successCount)
                .addKeyValue("failure_count", failureCount)
                .log("Matching offer expiration batch completed");
    }

    private void logExpirationBatchFailure(
            int requestedCount,
            int successCount,
            int failureCount,
            Instant startedAt,
            RuntimeException exception
    ) {
        log.atWarn()
                .addKeyValue("event", "matching.offer.expiration.batch.failed")
                .addKeyValue("job_name", JOB_NAME)
                .addKeyValue("status", "failed")
                .addKeyValue("duration_ms", elapsedMillis(startedAt))
                .addKeyValue("requested_count", requestedCount)
                .addKeyValue("success_count", successCount)
                .addKeyValue("failure_count", failureCount)
                .setCause(exception)
                .log("Matching offer expiration batch failed");
    }

    private long elapsedMillis(Instant startedAt) {
        return Duration.between(startedAt, clock.instant()).toMillis();
    }
}
