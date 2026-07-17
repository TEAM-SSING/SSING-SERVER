package org.sopt.ssingserver.domain.instructor.dev.service;

import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.instructor.dev.dto.request.ExecuteDevInstructorActionRequest;
import org.sopt.ssingserver.domain.instructor.dev.dto.response.DevInstructorActionExecutionResponse;
import org.sopt.ssingserver.domain.instructor.dev.error.DevInstructorErrorCode;
import org.sopt.ssingserver.global.error.BusinessException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Profile({"local", "dev"})
@ConditionalOnProperty(name = "ssing.dev-instructor-actions.enabled", havingValue = "true")
@Service
@RequiredArgsConstructor
public class DevInstructorActionService {

    private final DevInstructorActionTransactionService transactionService;

    public DevInstructorActionExecutionResponse execute(
            Long memberId,
            ExecuteDevInstructorActionRequest request
    ) {
        try {
            return transactionService.execute(memberId, request);
        } catch (PessimisticLockingFailureException exception) {
            // 동시에 누른 요청의 잠금 실패만 최신 상태 재조회가 필요한 409로 바꾼다.
            throw new BusinessException(DevInstructorErrorCode.DEV_INSTRUCTOR_STATE_CHANGED, exception);
        }
    }
}
