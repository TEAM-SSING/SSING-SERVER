package org.sopt.ssingserver.domain.matching.controller.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.sopt.ssingserver.domain.matching.dto.response.ConsumerMatchingStatusResponse;
import org.sopt.ssingserver.global.response.BaseResponse;
import org.sopt.ssingserver.global.response.docs.CommonErrorResponseDocs;
import org.sopt.ssingserver.global.security.access.CurrentMember;
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
    @ApiResponse(
            responseCode = "401",
            description = "인증 실패",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = CommonErrorResponseDocs.class),
                    examples = {
                            @ExampleObject(
                                    name = "인증 정보 없음",
                                    value = """
                                            {
                                              "success": false,
                                              "code": "UNAUTHENTICATED",
                                              "message": "로그인이 필요합니다.",
                                              "requestId": "req_abc123"
                                            }
                                            """
                            ),
                            @ExampleObject(
                                    name = "유효하지 않은 토큰",
                                    value = """
                                            {
                                              "success": false,
                                              "code": "AUTH_INVALID_TOKEN",
                                              "message": "유효하지 않은 토큰입니다.",
                                              "requestId": "req_abc123"
                                            }
                                            """
                            ),
                            @ExampleObject(
                                    name = "만료된 토큰",
                                    value = """
                                            {
                                              "success": false,
                                              "code": "AUTH_TOKEN_EXPIRED",
                                              "message": "로그인이 만료되었습니다. 다시 로그인해주세요.",
                                              "requestId": "req_abc123"
                                            }
                                            """
                            )
                    }
            )
    )
    @ApiResponse(
            responseCode = "403",
            description = "본인 소유의 매칭 요청이 아님",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = CommonErrorResponseDocs.class),
                    examples = @ExampleObject(
                            value = """
                                    {
                                      "success": false,
                                      "code": "FORBIDDEN",
                                      "message": "접근 권한이 없습니다.",
                                      "requestId": "req_abc123"
                                    }
                                    """
                    )
            )
    )
    @ApiResponse(
            responseCode = "404",
            description = "존재하지 않는 매칭 요청",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = CommonErrorResponseDocs.class),
                    examples = @ExampleObject(
                            value = """
                                    {
                                      "success": false,
                                      "code": "MATCHING_REQUEST_NOT_FOUND",
                                      "message": "존재하지 않는 매칭 요청입니다.",
                                      "requestId": "req_abc123"
                                    }
                                    """
                    )
            )
    )
    @ApiResponse(
            responseCode = "500",
            description = "서버 내부 오류",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = CommonErrorResponseDocs.class),
                    examples = @ExampleObject(
                            value = """
                                    {
                                      "success": false,
                                      "code": "INTERNAL_ERROR",
                                      "message": "서버 내부 오류가 발생했습니다.",
                                      "requestId": "req_abc123"
                                    }
                                    """
                    )
            )
    )
    ResponseEntity<BaseResponse<ConsumerMatchingStatusResponse>> getStatus(
            @Parameter(hidden = true)
            CurrentMember currentMember,
            @Parameter(description = "매칭 요청 ID")
            @PathVariable Long matchingRequestId
    );
}
