package org.sopt.ssingserver.domain.matching.controller.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.sopt.ssingserver.domain.auth.error.AuthErrorCode;
import org.sopt.ssingserver.domain.matching.dto.response.ConsumerMatchingStatusResponse;
import org.sopt.ssingserver.domain.matching.error.MatchingErrorCode;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.sopt.ssingserver.global.response.BaseResponse;
import org.sopt.ssingserver.global.security.access.CurrentMember;
import org.sopt.ssingserver.global.swagger.error.ApiErrorCodes;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;

@Tag(name = "Matching", description = "즉시 매칭 API")
public interface MatchingApiDocs {

    @Operation(
            summary = "소비자 매칭 진행 상태 조회",
            description = "소비자가 생성한 즉시 매칭 요청의 현재 진행 상태를 조회합니다. 강사가 수락한 뒤에는 저장된 강사 레벨을 포함한 프로필 요약을 반환하고, 최종 확인·결제·확정 단계에서는 제안 시점에 고정된 가격 요약을 함께 반환합니다.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponse(responseCode = "200", description = "매칭 진행 상태 조회 성공")
    @ApiErrorCodes(
            type = CommonErrorCode.class,
            names = {"BAD_REQUEST", "UNAUTHENTICATED", "FORBIDDEN", "INTERNAL_ERROR"}
    )
    @ApiErrorCodes(
            type = AuthErrorCode.class,
            names = {"AUTH_INVALID_TOKEN", "AUTH_TOKEN_EXPIRED"}
    )
    @ApiErrorCodes(type = MatchingErrorCode.class, names = "MATCHING_REQUEST_NOT_FOUND")
    ResponseEntity<BaseResponse<ConsumerMatchingStatusResponse>> getStatus(
            @Parameter(hidden = true)
            CurrentMember currentMember,
            @Parameter(description = "매칭 요청 ID")
            @PathVariable Long matchingRequestId
    );
}
