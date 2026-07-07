package org.sopt.ssingserver.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class MatchingAfterCommitExecutorTest {

    @Test
    void execute는_트랜잭션_동기화가_없으면_즉시_실행한다() {
        MatchingAfterCommitExecutor executor = new MatchingAfterCommitExecutor();
        Runnable action = mock(Runnable.class);

        executor.execute("test-action", action);

        verify(action).run();
    }

    @Test
    void execute는_트랜잭션_동기화가_있으면_커밋후_실행한다() {
        MatchingAfterCommitExecutor executor = new MatchingAfterCommitExecutor();
        Runnable action = mock(Runnable.class);
        TransactionSynchronizationManager.initSynchronization();

        try {
            executor.execute("test-action", action);

            verifyNoInteractions(action);

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            verify(action).run();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void execute는_트랜잭션_동기화가_없으면_실행_예외를_전파한다() {
        MatchingAfterCommitExecutor executor = new MatchingAfterCommitExecutor();

        assertThatThrownBy(() -> executor.execute("test-action", () -> {
            throw new IllegalStateException("failed");
        })).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void execute는_커밋후_작업_예외를_원_흐름으로_전파하지_않는다() {
        MatchingAfterCommitExecutor executor = new MatchingAfterCommitExecutor();
        TransactionSynchronizationManager.initSynchronization();

        try {
            executor.execute("test-action", () -> {
                throw new IllegalStateException("failed");
            });

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
