package org.sopt.ssingserver.domain.matching.service;

import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.matching.event.MatchingDomainEvent;
import org.sopt.ssingserver.domain.matching.event.MatchingEventPublisher;
import org.springframework.stereotype.Component;

// 매칭 도메인 이벤트를 DB 커밋 이후 발행하도록 예약하는 공용 경계
@Component
@RequiredArgsConstructor
public class MatchingEventDispatcher {

    private static final String PUBLISH_TASK_NAME = "matching-domain-event-publish";

    private final MatchingEventPublisher matchingEventPublisher;
    private final MatchingAfterCommitExecutor matchingAfterCommitExecutor;

    public void publishAfterCommit(MatchingDomainEvent event) {
        matchingAfterCommitExecutor.execute(PUBLISH_TASK_NAME, () -> matchingEventPublisher.publish(event));
    }
}
