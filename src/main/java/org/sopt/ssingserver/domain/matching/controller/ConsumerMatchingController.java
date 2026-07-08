package org.sopt.ssingserver.domain.matching.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.matching.controller.docs.ConsumerMatchingApiDocs;
import org.sopt.ssingserver.domain.matching.dto.command.MatchingCreationCommand;
import org.sopt.ssingserver.domain.matching.dto.request.CreateConsumerMatchingRequest;
import org.sopt.ssingserver.domain.matching.dto.response.ConsumerMatchingRequestCreateResponse;
import org.sopt.ssingserver.domain.matching.dto.result.MatchingCreationResult;
import org.sopt.ssingserver.domain.matching.response.MatchingSuccessCode;
import org.sopt.ssingserver.domain.matching.service.MatchingOrchestrationService;
import org.sopt.ssingserver.global.response.BaseResponse;
import org.sopt.ssingserver.global.response.SuccessResponseFactory;
import org.sopt.ssingserver.global.security.access.AccessPolicy;
import org.sopt.ssingserver.global.security.access.CurrentMember;
import org.sopt.ssingserver.global.security.access.RequireAccess;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/consumer/matching-requests")
public class ConsumerMatchingController implements ConsumerMatchingApiDocs {

    private final MatchingOrchestrationService matchingOrchestrationService;

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
}
