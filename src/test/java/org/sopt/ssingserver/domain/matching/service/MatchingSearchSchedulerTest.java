package org.sopt.ssingserver.domain.matching.service;

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
}
