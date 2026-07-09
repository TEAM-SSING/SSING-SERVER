package org.sopt.ssingserver.domain.matching.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.matching.controller.docs.ConsumerMatchingApiDocs;
import org.sopt.ssingserver.domain.matching.dto.command.MatchingCreationCommand;
import org.sopt.ssingserver.domain.matching.dto.request.CreateConsumerMatchingRequest;
import org.sopt.ssingserver.domain.matching.dto.request.RespondConsumerMatchingConfirmationRequest;
import org.sopt.ssingserver.domain.matching.dto.response.ConsumerMatchingCancellationResponse;
import org.sopt.ssingserver.domain.matching.dto.response.ConsumerMatchingConfirmationResponse;
import org.sopt.ssingserver.domain.matching.dto.response.ConsumerMatchingPaymentResponse;
import org.sopt.ssingserver.domain.matching.dto.response.ConsumerMatchingRequestCreateResponse;
import org.sopt.ssingserver.domain.matching.dto.result.ConsumerMatchingConfirmationResult;
import org.sopt.ssingserver.domain.matching.dto.result.ConsumerMatchingPaymentResult;
import org.sopt.ssingserver.domain.matching.dto.result.MatchingCancellationResult;
import org.sopt.ssingserver.domain.matching.dto.result.MatchingCreationResult;
import org.sopt.ssingserver.domain.matching.response.MatchingSuccessCode;
import org.sopt.ssingserver.domain.matching.service.ConsumerMatchingProgressService;
import org.sopt.ssingserver.domain.matching.service.MatchingCancellationService;
import org.sopt.ssingserver.domain.matching.service.MatchingOrchestrationService;
import org.sopt.ssingserver.global.response.BaseResponse;
import org.sopt.ssingserver.global.response.SuccessResponseFactory;
import org.sopt.ssingserver.global.security.access.AccessPolicy;
import org.sopt.ssingserver.global.security.access.CurrentMember;
import org.sopt.ssingserver.global.security.access.RequireAccess;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/consumer/matching-requests")
public class ConsumerMatchingController implements ConsumerMatchingApiDocs {

    private final MatchingOrchestrationService matchingOrchestrationService;
    private final MatchingCancellationService matchingCancellationService;
    private final ConsumerMatchingProgressService consumerMatchingProgressService;

    @Override
    @RequireAccess(AccessPolicy.CONSUMER)
    @PostMapping
    public ResponseEntity<BaseResponse<ConsumerMatchingRequestCreateResponse>> createMatchingRequest(
            CurrentMember currentMember,
            @Valid @RequestBody CreateConsumerMatchingRequest request
    ) {
        MatchingCreationCommand command = request.toCommand(currentMember.memberId());
        MatchingCreationResult result = matchingOrchestrationService.createImmediateMatchingRequest(command);
        ConsumerMatchingRequestCreateResponse response = ConsumerMatchingRequestCreateResponse.from(result);

        return SuccessResponseFactory.success(MatchingSuccessCode.MATCHING_REQUEST_CREATED, response);
    }

    @Override
    @RequireAccess(AccessPolicy.CONSUMER)
    @PostMapping("/{matchingRequestId}/cancellation")
    public ResponseEntity<BaseResponse<ConsumerMatchingCancellationResponse>> cancelMatchingRequest(
            CurrentMember currentMember,
            @PathVariable Long matchingRequestId
    ) {
        MatchingCancellationResult result = matchingCancellationService.cancel(
                currentMember.memberId(),
                matchingRequestId
        );
        ConsumerMatchingCancellationResponse response = ConsumerMatchingCancellationResponse.from(result);

        return SuccessResponseFactory.success(MatchingSuccessCode.MATCHING_REQUEST_CANCELED, response);
    }

    @Override
    @RequireAccess(AccessPolicy.CONSUMER)
    @PatchMapping("/{matchingRequestId}/confirmation")
    public ResponseEntity<BaseResponse<ConsumerMatchingConfirmationResponse>> respondMatchingConfirmation(
            CurrentMember currentMember,
            @PathVariable Long matchingRequestId,
            @Valid @RequestBody RespondConsumerMatchingConfirmationRequest request
    ) {
        ConsumerMatchingConfirmationResult result = consumerMatchingProgressService.respond(
                currentMember.memberId(),
                matchingRequestId,
                request.decision()
        );
        ConsumerMatchingConfirmationResponse response = ConsumerMatchingConfirmationResponse.from(result);

        return SuccessResponseFactory.success(MatchingSuccessCode.MATCHING_CONFIRMATION_UPDATED, response);
    }

    @Override
    @RequireAccess(AccessPolicy.CONSUMER)
    @PostMapping("/{matchingRequestId}/payment")
    public ResponseEntity<BaseResponse<ConsumerMatchingPaymentResponse>> completeMatchingPayment(
            CurrentMember currentMember,
            @PathVariable Long matchingRequestId
    ) {
        ConsumerMatchingPaymentResult result = consumerMatchingProgressService.completePayment(
                currentMember.memberId(),
                matchingRequestId
        );
        ConsumerMatchingPaymentResponse response = ConsumerMatchingPaymentResponse.from(result);

        return SuccessResponseFactory.success(MatchingSuccessCode.MATCHING_PAYMENT_COMPLETED, response);
    }
}
