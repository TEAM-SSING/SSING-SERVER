package org.sopt.ssingserver.domain.matching.dev.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sopt.ssingserver.domain.matching.dev.dto.request.ExecuteDevMatchingActionRequest;
import org.sopt.ssingserver.domain.matching.dev.dto.response.DevMatchingActionExecutionResponse;
import org.sopt.ssingserver.domain.matching.dev.dto.response.DevMatchingActionPreviewResponse;
import org.sopt.ssingserver.domain.matching.dev.dto.response.DevMatchingPersonResponse;
import org.sopt.ssingserver.domain.matching.dev.dto.response.DevMatchingRequestDetailResponse;
import org.sopt.ssingserver.domain.matching.dev.dto.response.DevMatchingRequestRelationResponse;
import org.sopt.ssingserver.domain.matching.dev.enums.DevMatchingActionKey;
import org.sopt.ssingserver.domain.matching.dev.enums.DevMatchingPersonRole;
import org.sopt.ssingserver.domain.matching.dev.enums.DevMatchingResolutionState;
import org.sopt.ssingserver.domain.matching.dev.error.DevMatchingErrorCode;
import org.sopt.ssingserver.domain.matching.enums.MatchingConfirmationDecision;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferDecision;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
import org.sopt.ssingserver.domain.matching.error.MatchingErrorCode;
import org.sopt.ssingserver.domain.matching.service.ConsumerMatchingProgressService;
import org.sopt.ssingserver.domain.matching.service.InstructorMatchingOfferService;
import org.sopt.ssingserver.global.error.BusinessException;

@ExtendWith(MockitoExtension.class)
class DevMatchingActionServiceTest {

    @Mock
    private DevMatchingQueryService queryService;

    @Mock
    private InstructorMatchingOfferService instructorMatchingOfferService;

    @Mock
    private ConsumerMatchingProgressService consumerMatchingProgressService;

    @Test
    void 강사수락은_조회관계에서_강사와_offer를_찾아_운영서비스를_호출하고_다시_조회한다() {
        DevMatchingActionService service = service();
        DevMatchingRequestDetailResponse before = detail(
                "token-before",
                MatchingStatus.WAITING_FOR_INSTRUCTOR,
                DevMatchingActionKey.INSTRUCTOR_ACCEPT,
                instructor(),
                false
        );
        DevMatchingRequestDetailResponse after = detail(
                "token-after",
                MatchingStatus.WAITING_FOR_CONFIRMATION,
                DevMatchingActionKey.CONSUMER_ACCEPT,
                consumer(),
                false
        );
        when(queryService.getRequest(301L)).thenReturn(before, after);

        DevMatchingActionExecutionResponse response = service.execute(
                301L,
                new ExecuteDevMatchingActionRequest(DevMatchingActionKey.INSTRUCTOR_ACCEPT, "token-before")
        );

        verify(instructorMatchingOfferService).respond(45L, 77L, MatchingOfferDecision.ACCEPTED);
        assertThat(response.actionKey()).isEqualTo(DevMatchingActionKey.INSTRUCTOR_ACCEPT);
        assertThat(response.actor()).isEqualTo(instructor());
        assertThat(response.before()).isSameAs(before);
        assertThat(response.after()).isSameAs(after);
    }

    @Test
    void 강습생수락과_결제완료는_조회관계의_강습생으로_운영서비스를_호출한다() {
        DevMatchingActionService service = service();
        DevMatchingRequestDetailResponse confirmation = detail(
                "confirmation-token",
                MatchingStatus.WAITING_FOR_CONFIRMATION,
                DevMatchingActionKey.CONSUMER_ACCEPT,
                consumer(),
                false
        );
        DevMatchingRequestDetailResponse payment = detail(
                "payment-token",
                MatchingStatus.PAYMENT_PENDING,
                DevMatchingActionKey.PAYMENT_COMPLETE,
                consumer(),
                false
        );
        DevMatchingRequestDetailResponse confirmed = detail(
                "confirmed-token",
                MatchingStatus.CONFIRMED,
                null,
                null,
                true
        );
        when(queryService.getRequest(301L)).thenReturn(confirmation, payment, payment, confirmed);

        service.execute(
                301L,
                new ExecuteDevMatchingActionRequest(DevMatchingActionKey.CONSUMER_ACCEPT, "confirmation-token")
        );
        service.execute(
                301L,
                new ExecuteDevMatchingActionRequest(DevMatchingActionKey.PAYMENT_COMPLETE, "payment-token")
        );

        verify(consumerMatchingProgressService).respond(12L, 301L, MatchingConfirmationDecision.ACCEPTED);
        verify(consumerMatchingProgressService).completePayment(12L, 301L);
    }

    @Test
    void stateToken이_다르면_운영서비스를_호출하지_않는다() {
        DevMatchingActionService service = service();
        when(queryService.getRequest(301L)).thenReturn(detail(
                "latest-token",
                MatchingStatus.WAITING_FOR_INSTRUCTOR,
                DevMatchingActionKey.INSTRUCTOR_ACCEPT,
                instructor(),
                false
        ));

        assertThatThrownBy(() -> service.execute(
                301L,
                new ExecuteDevMatchingActionRequest(DevMatchingActionKey.INSTRUCTOR_ACCEPT, "stale-token")
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(DevMatchingErrorCode.DEV_MATCHING_STATE_CHANGED)
                );

        verifyNoInteractions(instructorMatchingOfferService, consumerMatchingProgressService);
    }

    @Test
    void 미리보기전용_거절동작은_실행하지_않는다() {
        DevMatchingActionService service = service();
        when(queryService.getRequest(301L)).thenReturn(detail(
                "token",
                MatchingStatus.WAITING_FOR_INSTRUCTOR,
                DevMatchingActionKey.INSTRUCTOR_REJECT,
                instructor(),
                true
        ));

        assertThatThrownBy(() -> service.execute(
                301L,
                new ExecuteDevMatchingActionRequest(DevMatchingActionKey.INSTRUCTOR_REJECT, "token")
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(DevMatchingErrorCode.DEV_MATCHING_ACTION_NOT_AVAILABLE)
                );

        verifyNoInteractions(instructorMatchingOfferService, consumerMatchingProgressService);
    }

    @Test
    void 실행직전_경쟁으로_운영서비스가_거절하고_token도_바뀌면_새로고침_충돌로_바꾼다() {
        DevMatchingActionService service = service();
        DevMatchingRequestDetailResponse before = detail(
                "token-before",
                MatchingStatus.WAITING_FOR_INSTRUCTOR,
                DevMatchingActionKey.INSTRUCTOR_ACCEPT,
                instructor(),
                false
        );
        DevMatchingRequestDetailResponse latest = detail(
                "token-latest",
                MatchingStatus.WAITING_FOR_CONFIRMATION,
                DevMatchingActionKey.CONSUMER_ACCEPT,
                consumer(),
                false
        );
        when(queryService.getRequest(301L)).thenReturn(before, latest);
        when(instructorMatchingOfferService.respond(45L, 77L, MatchingOfferDecision.ACCEPTED))
                .thenThrow(new BusinessException(MatchingErrorCode.MATCHING_OFFER_ALREADY_RESPONDED));

        assertThatThrownBy(() -> service.execute(
                301L,
                new ExecuteDevMatchingActionRequest(DevMatchingActionKey.INSTRUCTOR_ACCEPT, "token-before")
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(DevMatchingErrorCode.DEV_MATCHING_STATE_CHANGED)
                );
    }

    private DevMatchingActionService service() {
        return new DevMatchingActionService(
                queryService,
                instructorMatchingOfferService,
                consumerMatchingProgressService
        );
    }

    private DevMatchingRequestDetailResponse detail(
            String stateToken,
            MatchingStatus matchingStatus,
            DevMatchingActionKey actionKey,
            DevMatchingPersonResponse actor,
            boolean previewOnly
    ) {
        List<DevMatchingActionPreviewResponse> actions = actionKey == null
                ? List.of()
                : List.of(new DevMatchingActionPreviewResponse(
                        actionKey,
                        actionKey.name(),
                        actor,
                        List.of(actor),
                        List.of(),
                        List.of(),
                        previewOnly
                ));
        return new DevMatchingRequestDetailResponse(
                Instant.parse("2026-07-16T00:00:00Z"),
                stateToken,
                301L,
                DevMatchingResolutionState.RESOLVED,
                matchingStatus,
                MatchingRequestStatus.MATCHED,
                null,
                List.of(consumer(), instructor()),
                List.of(new DevMatchingRequestRelationResponse(
                        301L,
                        12L,
                        302L,
                        98L,
                        77L,
                        matchingStatus == MatchingStatus.PAYMENT_PENDING ? 401L : null,
                        matchingStatus
                )),
                List.of(),
                List.of(),
                actions,
                List.of(),
                List.of()
        );
    }

    private DevMatchingPersonResponse consumer() {
        return new DevMatchingPersonResponse(
                DevMatchingPersonRole.CONSUMER,
                12L,
                null,
                "consumer-default",
                "강습생"
        );
    }

    private DevMatchingPersonResponse instructor() {
        return new DevMatchingPersonResponse(
                DevMatchingPersonRole.INSTRUCTOR,
                45L,
                5L,
                "instructor-approved-default",
                "강사"
        );
    }
}
