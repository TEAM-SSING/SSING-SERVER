package org.sopt.ssingserver.domain.matching.dev.service;

import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.matching.dev.dto.request.ExecuteDevMatchingActionRequest;
import org.sopt.ssingserver.domain.matching.dev.dto.response.DevMatchingActionExecutionResponse;
import org.sopt.ssingserver.domain.matching.dev.dto.response.DevMatchingActionPreviewResponse;
import org.sopt.ssingserver.domain.matching.dev.dto.response.DevMatchingPersonResponse;
import org.sopt.ssingserver.domain.matching.dev.dto.response.DevMatchingRequestDetailResponse;
import org.sopt.ssingserver.domain.matching.dev.dto.response.DevMatchingRequestRelationResponse;
import org.sopt.ssingserver.domain.matching.dev.enums.DevMatchingActionKey;
import org.sopt.ssingserver.domain.matching.dev.enums.DevMatchingResolutionState;
import org.sopt.ssingserver.domain.matching.dev.error.DevMatchingErrorCode;
import org.sopt.ssingserver.domain.matching.enums.MatchingConfirmationDecision;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferDecision;
import org.sopt.ssingserver.domain.matching.service.ConsumerMatchingProgressService;
import org.sopt.ssingserver.domain.matching.service.InstructorMatchingOfferService;
import org.sopt.ssingserver.global.error.BusinessException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Profile({"local", "dev"})
@ConditionalOnProperty(name = "ssing.dev-matching-actions.enabled", havingValue = "true")
@Service
@RequiredArgsConstructor
public class DevMatchingActionService {

    private final DevMatchingQueryService devMatchingQueryService;
    private final InstructorMatchingOfferService instructorMatchingOfferService;
    private final ConsumerMatchingProgressService consumerMatchingProgressService;

    // 조회 트랜잭션과 운영 상태 전이 트랜잭션을 한 덩어리로 묶지 않고, 커밋 뒤 실제 결과를 다시 읽는다.
    public DevMatchingActionExecutionResponse execute(
            Long matchingRequestId,
            ExecuteDevMatchingActionRequest request
    ) {
        DevMatchingRequestDetailResponse before = devMatchingQueryService.getRequest(matchingRequestId);
        validateStateToken(request.stateToken(), before);
        DevMatchingActionPreviewResponse action = findExecutableAction(before, request.actionKey());
        DevMatchingRequestRelationResponse relation = singleRelation(before, matchingRequestId);

        try {
            executeProductionAction(request.actionKey(), action.actor(), relation, matchingRequestId);
        } catch (BusinessException exception) {
            rethrowAsStateChangedWhenNeeded(matchingRequestId, request.stateToken(), exception);
            throw exception;
        }

        DevMatchingRequestDetailResponse after = devMatchingQueryService.getRequest(matchingRequestId);
        return new DevMatchingActionExecutionResponse(
                request.actionKey(),
                action.actor(),
                before,
                after
        );
    }

    private void validateStateToken(String expectedStateToken, DevMatchingRequestDetailResponse latest) {
        if (!Objects.equals(expectedStateToken, latest.stateToken())) {
            throw new BusinessException(DevMatchingErrorCode.DEV_MATCHING_STATE_CHANGED);
        }
    }

    private DevMatchingActionPreviewResponse findExecutableAction(
            DevMatchingRequestDetailResponse detail,
            DevMatchingActionKey actionKey
    ) {
        if (detail.resolutionState() != DevMatchingResolutionState.RESOLVED) {
            throw new BusinessException(DevMatchingErrorCode.DEV_MATCHING_ACTION_NOT_AVAILABLE);
        }
        return detail.availableActions().stream()
                .filter(action -> action.actionKey() == actionKey)
                .filter(action -> !action.previewOnly())
                .filter(action -> action.actor() != null && action.actor().memberId() != null)
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        DevMatchingErrorCode.DEV_MATCHING_ACTION_NOT_AVAILABLE
                ));
    }

    private DevMatchingRequestRelationResponse singleRelation(
            DevMatchingRequestDetailResponse detail,
            Long matchingRequestId
    ) {
        if (detail.requestRelations().size() != 1) {
            throw new BusinessException(DevMatchingErrorCode.DEV_MATCHING_ACTION_NOT_AVAILABLE);
        }
        DevMatchingRequestRelationResponse relation = detail.requestRelations().getFirst();
        if (!Objects.equals(relation.matchingRequestId(), matchingRequestId)) {
            throw new BusinessException(DevMatchingErrorCode.DEV_MATCHING_ACTION_NOT_AVAILABLE);
        }
        return relation;
    }

    private void executeProductionAction(
            DevMatchingActionKey actionKey,
            DevMatchingPersonResponse actor,
            DevMatchingRequestRelationResponse relation,
            Long matchingRequestId
    ) {
        switch (actionKey) {
            case INSTRUCTOR_ACCEPT -> {
                if (relation.offerId() == null) {
                    throw new BusinessException(DevMatchingErrorCode.DEV_MATCHING_ACTION_NOT_AVAILABLE);
                }
                instructorMatchingOfferService.respond(
                        actor.memberId(),
                        relation.offerId(),
                        MatchingOfferDecision.ACCEPTED
                );
            }
            case CONSUMER_ACCEPT -> consumerMatchingProgressService.respond(
                    actor.memberId(),
                    matchingRequestId,
                    MatchingConfirmationDecision.ACCEPTED
            );
            case PAYMENT_COMPLETE -> consumerMatchingProgressService.completePayment(
                    actor.memberId(),
                    matchingRequestId
            );
            case INSTRUCTOR_REJECT, CONSUMER_REJECT -> throw new BusinessException(
                    DevMatchingErrorCode.DEV_MATCHING_ACTION_NOT_AVAILABLE
            );
        }
    }

    private void rethrowAsStateChangedWhenNeeded(
            Long matchingRequestId,
            String expectedStateToken,
            BusinessException original
    ) {
        try {
            DevMatchingRequestDetailResponse latest = devMatchingQueryService.getRequest(matchingRequestId);
            if (!Objects.equals(expectedStateToken, latest.stateToken())) {
                throw new BusinessException(DevMatchingErrorCode.DEV_MATCHING_STATE_CHANGED, original);
            }
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == DevMatchingErrorCode.DEV_MATCHING_STATE_CHANGED) {
                throw exception;
            }
        } catch (RuntimeException ignored) {
            // 재조회 자체가 실패하면 원래 운영 서비스 오류를 보존한다.
        }
    }
}
