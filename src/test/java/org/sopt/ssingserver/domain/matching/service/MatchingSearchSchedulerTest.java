package org.sopt.ssingserver.domain.matching.service;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MatchingSearchSchedulerTest {

    @Mock
    private MatchingSearchTriggerService matchingSearchTriggerService;

    @Test
    void runScheduledSearch는_SEARCHING_요청_재탐색을_위임한다() {
        MatchingSearchScheduler scheduler = new MatchingSearchScheduler(matchingSearchTriggerService);

        scheduler.runScheduledSearch();

        verify(matchingSearchTriggerService).triggerAllRequested();
    }
}
