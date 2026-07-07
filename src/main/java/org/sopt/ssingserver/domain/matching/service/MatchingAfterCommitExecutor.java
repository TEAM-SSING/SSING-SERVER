package org.sopt.ssingserver.domain.matching.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// 매칭 도메인 DB 커밋 이후 후속 작업 실행 경계
@Component
public class MatchingAfterCommitExecutor {

    private static final Logger log = LoggerFactory.getLogger(MatchingAfterCommitExecutor.class);

    // 트랜잭션 존재 시 afterCommit 등록, 단위 테스트/직접 호출 환경의 즉시 실행
    public void execute(String taskName, Runnable action) {
        // 트랜잭션 동기화 없음, 이미 커밋 경계 밖인 단위 테스트/직접 호출의 즉시 실행
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }

        // 트랜잭션 동기화 있음, rollback 시 후속 탐색/알림 미실행을 위한 커밋 이후 예약
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                executeAfterCommit(taskName, action);
            }
        });
    }

    // 커밋 이후 후속 작업 실패의 원 요청 성공 응답 전파 방지
    private void executeAfterCommit(String taskName, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            // afterCommit 단계 실패 기록, 스케줄러 재시도 가능 흐름을 막지 않는 구조화 로그
            log.atError()
                    .addKeyValue("event", "matching.after_commit.failed")
                    .addKeyValue("task", taskName)
                    .setCause(exception)
                    .log("Matching after commit action failed");
        }
    }
}
