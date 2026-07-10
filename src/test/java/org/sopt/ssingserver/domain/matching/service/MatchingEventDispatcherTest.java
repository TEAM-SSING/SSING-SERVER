package org.sopt.ssingserver.domain.matching.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.matching.event.MatchingEventPublisher;
import org.sopt.ssingserver.domain.matching.event.MatchingOfferCreatedEvent;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class MatchingEventDispatcherTest {

    @Test
    void publishAfterCommit는_트랜잭션_동기화가_없으면_같은_이벤트를_즉시_발행한다() {
        MatchingEventPublisher matchingEventPublisher = mock(MatchingEventPublisher.class);
        MatchingEventDispatcher dispatcher = new MatchingEventDispatcher(
                matchingEventPublisher,
                new MatchingAfterCommitExecutor()
        );
        MatchingOfferCreatedEvent event = matchingOfferCreatedEvent();

        dispatcher.publishAfterCommit(event);

        verify(matchingEventPublisher, times(1)).publish(same(event));
    }

    @Test
    void publishAfterCommit는_트랜잭션_동기화가_있으면_afterCommit까지_발행하지_않는다() {
        MatchingEventPublisher matchingEventPublisher = mock(MatchingEventPublisher.class);
        MatchingEventDispatcher dispatcher = new MatchingEventDispatcher(
                matchingEventPublisher,
                new MatchingAfterCommitExecutor()
        );
        MatchingOfferCreatedEvent event = matchingOfferCreatedEvent();
        TransactionSynchronizationManager.initSynchronization();

        try {
            dispatcher.publishAfterCommit(event);

            verifyNoInteractions(matchingEventPublisher);

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            verify(matchingEventPublisher, times(1)).publish(same(event));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void publishAfterCommit는_afterCommit이_실행되지_않으면_이벤트를_발행하지_않는다() {
        MatchingEventPublisher matchingEventPublisher = mock(MatchingEventPublisher.class);
        MatchingEventDispatcher dispatcher = new MatchingEventDispatcher(
                matchingEventPublisher,
                new MatchingAfterCommitExecutor()
        );
        MatchingOfferCreatedEvent event = matchingOfferCreatedEvent();
        TransactionSynchronizationManager.initSynchronization();

        try {
            dispatcher.publishAfterCommit(event);

            verifyNoInteractions(matchingEventPublisher);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verifyNoInteractions(matchingEventPublisher);
    }

    private MatchingOfferCreatedEvent matchingOfferCreatedEvent() {
        return new MatchingOfferCreatedEvent(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                Instant.parse("2026-07-10T00:00:00Z"),
                1L,
                2L,
                3L,
                120,
                4L
        );
    }
}
