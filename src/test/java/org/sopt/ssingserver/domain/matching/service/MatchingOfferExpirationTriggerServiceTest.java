package org.sopt.ssingserver.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
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
    void expireOverdueOffers는_항목_실패를_기록하고_다음_제안을_계속_처리한다() {
        MatchingOfferExpirationTriggerService service = createService();
        when(matchingOfferRepository.findIdsByStatusAndExpiresAtLessThanEqualOrderByIdAsc(
                MatchingOfferStatus.OFFERED,
                FIXED_CLOCK.instant(),
                PageRequest.of(0, 100)
        )).thenReturn(List.of(10L, 11L, 12L));
        doAnswer(invocation -> {
            Long matchingOfferId = invocation.getArgument(0);
            if (matchingOfferId.equals(11L)) {
                throw new IllegalStateException("expiration failed");
            }
            return null;
        })
                .when(matchingOfferExpirationService)
                .expireOffer(anyLong());

        Logger logger = (Logger) LoggerFactory.getLogger(MatchingOfferExpirationTriggerService.class);
        Level originalLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.setLevel(Level.INFO);
        logger.addAppender(appender);

        try {
            assertThatCode(service::expireOverdueOffers).doesNotThrowAnyException();

            verify(matchingOfferExpirationService).expireOffer(10L);
            verify(matchingOfferExpirationService).expireOffer(11L);
            verify(matchingOfferExpirationService).expireOffer(12L);

            ILoggingEvent failureEvent = findLogEvent(appender, "matching.offer.expiration.failed");
            assertThat(failureEvent.getLevel()).isSameAs(Level.WARN);
            assertThat(keyValueMap(failureEvent))
                    .containsEntry("job_name", "matching-offer-expiration")
                    .containsEntry("matching_offer_id", "11");

            ILoggingEvent summaryEvent = findLogEvent(appender, "matching.offer.expiration.batch.completed");
            assertThat(summaryEvent.getLevel()).isSameAs(Level.INFO);
            assertThat(keyValueMap(summaryEvent))
                    .containsEntry("job_name", "matching-offer-expiration")
                    .containsEntry("status", "completed")
                    .containsEntry("duration_ms", 0L)
                    .containsEntry("requested_count", 3)
                    .containsEntry("success_count", 2)
                    .containsEntry("failure_count", 1);
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(originalLevel);
        }
    }

    @Test
    void expireOverdueOffers는_만료할_제안이_없어도_배치_요약을_기록한다() {
        MatchingOfferExpirationTriggerService service = createService();
        when(matchingOfferRepository.findIdsByStatusAndExpiresAtLessThanEqualOrderByIdAsc(
                MatchingOfferStatus.OFFERED,
                FIXED_CLOCK.instant(),
                PageRequest.of(0, 100)
        )).thenReturn(List.of());

        Logger logger = (Logger) LoggerFactory.getLogger(MatchingOfferExpirationTriggerService.class);
        Level originalLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.setLevel(Level.INFO);
        logger.addAppender(appender);

        try {
            service.expireOverdueOffers();

            verifyNoInteractions(matchingOfferExpirationService);
            ILoggingEvent summaryEvent = findLogEvent(appender, "matching.offer.expiration.batch.completed");
            assertThat(keyValueMap(summaryEvent))
                    .containsEntry("status", "completed")
                    .containsEntry("requested_count", 0)
                    .containsEntry("success_count", 0)
                    .containsEntry("failure_count", 0);
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(originalLevel);
        }
    }

    @Test
    void expireOverdueOffers는_조회_실패를_배치_실패로_기록하고_예외를_밖으로_던지지_않는다() {
        MatchingOfferExpirationTriggerService service = createService();
        when(matchingOfferRepository.findIdsByStatusAndExpiresAtLessThanEqualOrderByIdAsc(
                MatchingOfferStatus.OFFERED,
                FIXED_CLOCK.instant(),
                PageRequest.of(0, 100)
        )).thenThrow(new IllegalStateException("scan failed"));

        Logger logger = (Logger) LoggerFactory.getLogger(MatchingOfferExpirationTriggerService.class);
        Level originalLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.setLevel(Level.INFO);
        logger.addAppender(appender);

        try {
            assertThatCode(service::expireOverdueOffers).doesNotThrowAnyException();

            verifyNoInteractions(matchingOfferExpirationService);
            ILoggingEvent failureEvent = findLogEvent(appender, "matching.offer.expiration.batch.failed");
            assertThat(failureEvent.getLevel()).isSameAs(Level.WARN);
            assertThat(keyValueMap(failureEvent))
                    .containsEntry("job_name", "matching-offer-expiration")
                    .containsEntry("status", "failed")
                    .containsEntry("duration_ms", 0L)
                    .containsEntry("requested_count", 0)
                    .containsEntry("success_count", 0)
                    .containsEntry("failure_count", 0);
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(originalLevel);
        }
    }

    private MatchingOfferExpirationTriggerService createService() {
        return new MatchingOfferExpirationTriggerService(
                matchingOfferRepository,
                matchingOfferExpirationService,
                FIXED_CLOCK
        );
    }

    private ILoggingEvent findLogEvent(ListAppender<ILoggingEvent> appender, String eventName) {
        return appender.list.stream()
                .filter(event -> eventName.equals(keyValueMap(event).get("event")))
                .findFirst()
                .orElseThrow();
    }

    private Map<String, Object> keyValueMap(ILoggingEvent event) {
        return event.getKeyValuePairs().stream()
                .collect(Collectors.toMap(keyValuePair -> keyValuePair.key, keyValuePair -> keyValuePair.value));
    }
}
