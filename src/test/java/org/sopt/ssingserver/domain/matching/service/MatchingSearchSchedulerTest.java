package org.sopt.ssingserver.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MatchingSearchSchedulerTest {

    @Mock
    private MatchingOfferExpirationTriggerService matchingOfferExpirationTriggerService;

    @Mock
    private MatchingSearchTriggerService matchingSearchTriggerService;

    @Test
    void runScheduledSearch는_제안만료를_정리한_뒤_SEARCHING_요청_재탐색을_위임한다() {
        MatchingSearchScheduler scheduler = new MatchingSearchScheduler(
                matchingOfferExpirationTriggerService,
                matchingSearchTriggerService
        );

        scheduler.runScheduledSearch();

        verify(matchingOfferExpirationTriggerService).expireOverdueOffers();
        verify(matchingSearchTriggerService).triggerAllRequested();
    }

    @Test
    void runScheduledSearch는_제안만료_정리가_실패해도_SEARCHING_요청_재탐색을_계속한다() {
        MatchingSearchScheduler scheduler = new MatchingSearchScheduler(
                matchingOfferExpirationTriggerService,
                matchingSearchTriggerService
        );
        doThrow(new IllegalStateException("expiration failed"))
                .when(matchingOfferExpirationTriggerService)
                .expireOverdueOffers();

        assertThatCode(scheduler::runScheduledSearch).doesNotThrowAnyException();

        verify(matchingOfferExpirationTriggerService).expireOverdueOffers();
        verify(matchingSearchTriggerService).triggerAllRequested();
    }

    @Test
    void runScheduledSearch는_SEARCHING_요청_재탐색이_실패해도_예외를_밖으로_던지지_않는다() {
        MatchingSearchScheduler scheduler = new MatchingSearchScheduler(
                matchingOfferExpirationTriggerService,
                matchingSearchTriggerService
        );
        doThrow(new IllegalStateException("search failed"))
                .when(matchingSearchTriggerService)
                .triggerAllRequested();

        assertThatCode(scheduler::runScheduledSearch).doesNotThrowAnyException();

        verify(matchingOfferExpirationTriggerService).expireOverdueOffers();
        verify(matchingSearchTriggerService).triggerAllRequested();
    }
}
