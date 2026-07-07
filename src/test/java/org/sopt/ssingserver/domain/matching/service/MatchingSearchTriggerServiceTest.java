package org.sopt.ssingserver.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestRepository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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

    @Test
    void triggerSearch는_커밋후_후속_DB쓰기를_위해_새_트랜잭션으로_실행된다() throws NoSuchMethodException {
        Method method = MatchingSearchTriggerService.class.getMethod("triggerSearch", Long.class);

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isSameAs(Propagation.REQUIRES_NEW);
    }

    private MatchingSearchTriggerService createService() {
        return new MatchingSearchTriggerService(
                matchingRequestRepository,
                matchingSearchService
        );
    }
}
