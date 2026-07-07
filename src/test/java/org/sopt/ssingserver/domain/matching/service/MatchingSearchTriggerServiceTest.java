package org.sopt.ssingserver.domain.matching.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestRepository;

@ExtendWith(MockitoExtension.class)
class MatchingSearchTriggerServiceTest {

    @Mock
    private MatchingRequestRepository matchingRequestRepository;

    @Mock
    private MatchingSearchService matchingSearchService;

    @Test
    void triggerSearch는_단건_탐색을_위임한다() {
        MatchingSearchTriggerService service = createService();

        service.triggerSearch(10L);

        verify(matchingSearchService).search(10L);
    }

    @Test
    void triggerAllRequested는_REQUESTED_요청_id를_조회해_단건_탐색으로_하나씩_위임한다() {
        MatchingSearchTriggerService service = createService();
        when(matchingRequestRepository.findIdsByStatusOrderByIdAsc(MatchingRequestStatus.REQUESTED))
                .thenReturn(List.of(10L, 20L));

        service.triggerAllRequested();

        verify(matchingSearchService).search(10L);
        verify(matchingSearchService).search(20L);
    }

    private MatchingSearchTriggerService createService() {
        return new MatchingSearchTriggerService(
                matchingRequestRepository,
                matchingSearchService
        );
    }
}
