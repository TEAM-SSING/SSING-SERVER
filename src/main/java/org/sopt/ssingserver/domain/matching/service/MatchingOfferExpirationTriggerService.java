package org.sopt.ssingserver.domain.matching.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferStatus;
import org.sopt.ssingserver.domain.matching.repository.MatchingOfferRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MatchingOfferExpirationTriggerService {

    private static final Logger log = LoggerFactory.getLogger(MatchingOfferExpirationTriggerService.class);
    private static final String JOB_NAME = "matching-offer-expiration";
    private static final int FAILURE_DETAIL_LIMIT = 10;
    // TODO: 유한 응답 시간 정책을 다시 켠 뒤 만료 대기 제안이 계속 100개를 넘으면 backlog metric과 배치 확장을 검토한다.
    private static final int OFFER_EXPIRATION_SCAN_BATCH_SIZE = 100;

    private final MatchingOfferRepository matchingOfferRepository;
    private final MatchingOfferExpirationService matchingOfferExpirationService;
    private final MatchingTimeoutPolicy matchingTimeoutPolicy;
    private final Clock clock;

    public void expireOverdueOffers() {
        if (!matchingTimeoutPolicy.shouldRunInstructorOfferExpiration()) {
            return;
        }

        Instant startedAt = clock.instant();
        List<Long> matchingOfferIds;
        try {
            matchingOfferIds = matchingOfferRepository.findIdsByStatusAndExpiresAtLessThanEqualOrderByIdAsc(
                    MatchingOfferStatus.OFFERED,
                    clock.instant(),
                    PageRequest.of(0, OFFER_EXPIRATION_SCAN_BATCH_SIZE)
            );
        } catch (RuntimeException exception) {
            logExpirationBatchFailure(startedAt, exception);
            return;
        }

        if (matchingOfferIds.isEmpty()) {
            return;
        }

        int successCount = 0;
        int failureCount = 0;
        // 전체 실패 수는 유지하되 건별 로그는 최대 10건만 보관해 장애 시 로그 폭증을 막는다.
        List<ExpirationFailureDetail> failureDetails = new ArrayList<>(FAILURE_DETAIL_LIMIT);

        for (Long matchingOfferId : matchingOfferIds) {
            try {
                matchingOfferExpirationService.expireOffer(matchingOfferId);
                successCount++;
            } catch (RuntimeException exception) {
                failureCount++;
                if (failureDetails.size() < FAILURE_DETAIL_LIMIT) {
                    failureDetails.add(new ExpirationFailureDetail(
                            String.valueOf(matchingOfferId),
                            exception.getClass().getName()
                    ));
                }
            }
        }

        String jobRunId = failureDetails.isEmpty() ? null : UUID.randomUUID().toString();
        for (ExpirationFailureDetail failureDetail : failureDetails) {
            logExpirationFailure(jobRunId, failureDetail);
        }
        logExpirationSummary(
                jobRunId,
                matchingOfferIds.size(),
                successCount,
                failureCount,
                startedAt
        );
    }

    private void logExpirationFailure(String jobRunId, ExpirationFailureDetail failureDetail) {
        log.atWarn()
                .addKeyValue("event", "matching.offer.expiration.failed")
                .addKeyValue("job_name", JOB_NAME)
                .addKeyValue("job_run_id", jobRunId)
                .addKeyValue("matching_offer_id", failureDetail.matchingOfferId())
                .addKeyValue("exception_type", failureDetail.exceptionType())
                .log("Matching offer expiration failed");
    }

    private void logExpirationSummary(
            String jobRunId,
            int processedCount,
            int successCount,
            int failureCount,
            Instant startedAt
    ) {
        LoggingEventBuilder eventBuilder = failureCount > 0 ? log.atWarn() : log.atInfo();
        eventBuilder
                .addKeyValue("event", "matching.offer.expiration.batch.completed")
                .addKeyValue("job_name", JOB_NAME)
                .addKeyValue("job_status", resolveJobStatus(successCount, failureCount))
                .addKeyValue("duration_ms", elapsedMillis(startedAt))
                .addKeyValue("processed_count", processedCount)
                .addKeyValue("success_count", successCount)
                .addKeyValue("failure_count", failureCount);
        if (jobRunId != null) {
            eventBuilder.addKeyValue("job_run_id", jobRunId);
        }
        eventBuilder.log("Matching offer expiration batch completed");
    }

    private void logExpirationBatchFailure(
            Instant startedAt,
            RuntimeException exception
    ) {
        log.atWarn()
                .addKeyValue("event", "matching.offer.expiration.batch.failed")
                .addKeyValue("job_name", JOB_NAME)
                .addKeyValue("job_status", "failed")
                .addKeyValue("duration_ms", elapsedMillis(startedAt))
                .addKeyValue("processed_count", 0)
                .addKeyValue("success_count", 0)
                .addKeyValue("failure_count", 0)
                .addKeyValue("exception_type", exception.getClass().getName())
                .log("Matching offer expiration batch failed");
    }

    private String resolveJobStatus(int successCount, int failureCount) {
        if (failureCount == 0) {
            return "success";
        }
        return successCount == 0 ? "failed" : "partial_failure";
    }

    private long elapsedMillis(Instant startedAt) {
        return Duration.between(startedAt, clock.instant()).toMillis();
    }

    private record ExpirationFailureDetail(
            String matchingOfferId,
            String exceptionType
    ) {
    }
}
