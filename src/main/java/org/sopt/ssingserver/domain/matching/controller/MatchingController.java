package org.sopt.ssingserver.domain.matching.controller;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.matching.controller.docs.MatchingApiDocs;
import org.sopt.ssingserver.domain.matching.dto.response.ConsumerMatchingStatusResponse;
import org.sopt.ssingserver.domain.matching.dto.result.MatchingStatusQueryResult;
import org.sopt.ssingserver.domain.matching.service.MatchingStatusQueryService;
import org.sopt.ssingserver.domain.matching.response.MatchingSuccessCode;
import org.sopt.ssingserver.global.response.BaseResponse;
import org.sopt.ssingserver.global.response.CommonSuccessCode;
import org.sopt.ssingserver.global.response.SuccessResponseFactory;
import org.sopt.ssingserver.global.security.access.AccessPolicy;
import org.sopt.ssingserver.global.security.access.CurrentMember;
import org.sopt.ssingserver.global.security.access.RequireAccess;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/consumer/matching-requests")
public class MatchingController implements MatchingApiDocs {

    private final MatchingStatusQueryService matchingStatusQueryService;

    @Override
    @RequireAccess(AccessPolicy.CONSUMER)
    @GetMapping("/active")
    public ResponseEntity<BaseResponse<ConsumerMatchingStatusResponse>> getActiveStatus(
            CurrentMember currentMember
    ) {
        Optional<MatchingStatusQueryResult> activeStatus =
                matchingStatusQueryService.getActiveStatus(currentMember.memberId());
        if (activeStatus.isEmpty()) {
            return SuccessResponseFactory.noContent(MatchingSuccessCode.NO_ACTIVE_MATCHING_REQUEST);
        }

        return SuccessResponseFactory.success(
                CommonSuccessCode.SUCCESS,
                ConsumerMatchingStatusResponse.from(activeStatus.get())
        );
    }

    // 인증된 소비자 본인 요청 확인 후 공통 성공 응답으로 감싸는 HTTP 경계
    @Override
    @RequireAccess(AccessPolicy.CONSUMER)
    @GetMapping("/{matchingRequestId}")
    public ResponseEntity<BaseResponse<ConsumerMatchingStatusResponse>> getStatus(
            CurrentMember currentMember,
            @PathVariable Long matchingRequestId
    ) {
        MatchingStatusQueryResult result = matchingStatusQueryService.getStatus(
                currentMember.memberId(),
                matchingRequestId
        );
        return SuccessResponseFactory.success(
                CommonSuccessCode.SUCCESS,
                ConsumerMatchingStatusResponse.from(result)
        );
    }
}
