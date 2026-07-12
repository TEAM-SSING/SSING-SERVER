package org.sopt.ssingserver.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferStatus;
import org.sopt.ssingserver.domain.matching.repository.MatchingOfferRepository;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class MatchingOfferExpirationTriggerServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-07T00:00:00Z"),
            ZoneOffset.UTC
    );

    @Mock
    private MatchingOfferRepository matchingOfferRepository;

    @Mock
    private MatchingOfferExpirationService matchingOfferExpirationService;

    @Test
    void expireOverdueOffers는_무기한정책이면_만료대상을_조회하지_않는다() {
        MatchingOfferExpirationTriggerService service = createService(new MatchingTimeoutPolicy());

        service.expireOverdueOffers();

        verifyNoInteractions(matchingOfferRepository);
        verifyNoInteractions(matchingOfferExpirationService);
    }

    @Test
    void expireOverdueOffers는_유한정책이면_만료대상_제안을_조회하고_각_제안을_처리한다() {
        MatchingOfferExpirationTriggerService service = createService(finiteTimeoutPolicy());
        when(matchingOfferRepository.findIdsByStatusAndExpiresAtLessThanEqualOrderByIdAsc(
                MatchingOfferStatus.OFFERED,
                FIXED_CLOCK.instant(),
                PageRequest.of(0, 100)
        )).thenReturn(List.of(10L, 11L, 12L));
        doAnswer(invocation -> null)
                .when(matchingOfferExpirationService)
                .expireOffer(anyLong());

        assertThatCode(service::expireOverdueOffers).doesNotThrowAnyException();

        verify(matchingOfferExpirationService).expireOffer(10L);
        verify(matchingOfferExpirationService).expireOffer(11L);
        verify(matchingOfferExpirationService).expireOffer(12L);
    }

    @Test
    void expireOverdueOffers는_만료대상이_없으면_로그를_남기지_않는다() {
        MatchingOfferExpirationTriggerService service = createService(finiteTimeoutPolicy());
        when(matchingOfferRepository.findIdsByStatusAndExpiresAtLessThanEqualOrderByIdAsc(
                MatchingOfferStatus.OFFERED,
                FIXED_CLOCK.instant(),
                PageRequest.of(0, 100)
        )).thenReturn(List.of());
        Logger logger = (Logger) LoggerFactory.getLogger(MatchingOfferExpirationTriggerService.class);
        ListAppender<ILoggingEvent> appender = attachAppender(logger);

        try {
            service.expireOverdueOffers();

            assertThat(appender.list).isEmpty();
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void expireOverdueOffers는_실패_상세를_10건으로_제한하고_전체_실패수를_요약한다() {
        MatchingOfferExpirationTriggerService service = createService(finiteTimeoutPolicy());
        List<Long> offerIds = LongStream.rangeClosed(1, 12).boxed().toList();
        when(matchingOfferRepository.findIdsByStatusAndExpiresAtLessThanEqualOrderByIdAsc(
                MatchingOfferStatus.OFFERED,
                FIXED_CLOCK.instant(),
                PageRequest.of(0, 100)
        )).thenReturn(offerIds);
        doThrow(new IllegalStateException("secret-detail"))
                .when(matchingOfferExpirationService)
                .expireOffer(anyLong());
        Logger logger = (Logger) LoggerFactory.getLogger(MatchingOfferExpirationTriggerService.class);
        ListAppender<ILoggingEvent> appender = attachAppender(logger);

        try {
            service.expireOverdueOffers();

            List<ILoggingEvent> details = appender.list.stream()
                    .filter(event -> "matching.offer.expiration.failed"
                            .equals(keyValueMap(event).get("event")))
                    .toList();
            assertThat(details).hasSize(10);
            assertThat(details).allSatisfy(event -> {
                assertThat(event.getLevel()).isSameAs(Level.WARN);
                assertThat(event.getThrowableProxy()).isNull();
                assertThat(keyValueMap(event))
                        .containsEntry("exception_type", IllegalStateException.class.getName());
            });

            ILoggingEvent summary = appender.list.getLast();
            assertThat(summary.getLevel()).isSameAs(Level.WARN);
            assertThat(summary.getThrowableProxy()).isNull();
            assertThat(keyValueMap(summary))
                    .containsEntry("event", "matching.offer.expiration.batch.completed")
                    .containsEntry("job_status", "failed")
                    .containsEntry("processed_count", 12)
                    .containsEntry("success_count", 0)
                    .containsEntry("failure_count", 12);
            Object jobRunId = keyValueMap(summary).get("job_run_id");
            assertThat(jobRunId).isInstanceOf(String.class);
            assertThat(details).allSatisfy(event ->
                    assertThat(keyValueMap(event)).containsEntry("job_run_id", jobRunId));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void expireOverdueOffers는_조회실패를_raw_exception_없이_한_번_요약한다() {
        MatchingOfferExpirationTriggerService service = createService(finiteTimeoutPolicy());
        when(matchingOfferRepository.findIdsByStatusAndExpiresAtLessThanEqualOrderByIdAsc(
                MatchingOfferStatus.OFFERED,
                FIXED_CLOCK.instant(),
                PageRequest.of(0, 100)
        )).thenThrow(new IllegalStateException("secret-database-detail"));
        Logger logger = (Logger) LoggerFactory.getLogger(MatchingOfferExpirationTriggerService.class);
        ListAppender<ILoggingEvent> appender = attachAppender(logger);

        try {
            service.expireOverdueOffers();

            assertThat(appender.list).hasSize(1);
            ILoggingEvent event = appender.list.getFirst();
            assertThat(event.getThrowableProxy()).isNull();
            assertThat(event.getFormattedMessage()).doesNotContain("secret-database-detail");
            assertThat(keyValueMap(event))
                    .containsEntry("event", "matching.offer.expiration.batch.failed")
                    .containsEntry("job_status", "failed")
                    .containsEntry("processed_count", 0)
                    .containsEntry("exception_type", IllegalStateException.class.getName());
        } finally {
            logger.detachAppender(appender);
        }
    }

    private MatchingOfferExpirationTriggerService createService(MatchingTimeoutPolicy matchingTimeoutPolicy) {
        return new MatchingOfferExpirationTriggerService(
                matchingOfferRepository,
                matchingOfferExpirationService,
                matchingTimeoutPolicy,
                FIXED_CLOCK
        );
    }

    private MatchingTimeoutPolicy finiteTimeoutPolicy() {
        return new MatchingTimeoutPolicy() {
            @Override
            public boolean shouldRunInstructorOfferExpiration() {
                return true;
            }
        };
    }

    private ListAppender<ILoggingEvent> attachAppender(Logger logger) {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private Map<String, Object> keyValueMap(ILoggingEvent event) {
        return event.getKeyValuePairs().stream()
                .collect(Collectors.toMap(keyValuePair -> keyValuePair.key, keyValuePair -> keyValuePair.value));
    }
}
