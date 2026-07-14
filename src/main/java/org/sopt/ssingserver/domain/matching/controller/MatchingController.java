package org.sopt.ssingserver.domain.matching.controller;

import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.matching.controller.docs.MatchingApiDocs;
import org.sopt.ssingserver.domain.matching.dto.response.ConsumerActiveMatchingResponse;
import org.sopt.ssingserver.domain.matching.dto.response.ConsumerMatchingStatusResponse;
import org.sopt.ssingserver.domain.matching.dto.result.MatchingStatusQueryResult;
import org.sopt.ssingserver.domain.matching.service.MatchingStatusQueryService;
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

    // 앱 복구 조회에서는 활성 요청 없음도 정상 상태로 보고 ACTIVE/NONE 응답으로 통일한다.
    @Override
    @RequireAccess(AccessPolicy.CONSUMER)
    @GetMapping("/active")
    public ResponseEntity<BaseResponse<ConsumerActiveMatchingResponse>> getActiveStatus(
            CurrentMember currentMember
    ) {
        ConsumerActiveMatchingResponse response = matchingStatusQueryService
                .getActiveStatus(currentMember.memberId())
                .map(ConsumerActiveMatchingResponse::active)
                .orElseGet(ConsumerActiveMatchingResponse::none);

        return SuccessResponseFactory.success(
                CommonSuccessCode.SUCCESS,
                response
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
