package org.sopt.ssingserver.domain.lesson.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class LessonAfterCommitExecutor {

    private static final Logger log = LoggerFactory.getLogger(LessonAfterCommitExecutor.class);

    public void execute(String taskName, Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                executeAfterCommit(taskName, action);
            }
        });
    }

    private void executeAfterCommit(String taskName, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            log.atError()
                    .addKeyValue("event", "lesson.after_commit.failed")
                    .addKeyValue("task", taskName)
                    .setCause(exception)
                    .log("Lesson after commit action failed");
        }
    }
}
