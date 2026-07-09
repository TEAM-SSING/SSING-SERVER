package org.sopt.ssingserver.domain.matching.controller.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.sopt.ssingserver.domain.matching.dto.request.RespondInstructorMatchingOfferRequest;
import org.sopt.ssingserver.domain.matching.dto.response.InstructorMatchingOfferDecisionResponse;
import org.sopt.ssingserver.domain.matching.dto.response.InstructorMatchingOffersResponse;
import org.sopt.ssingserver.global.response.BaseResponse;
import org.sopt.ssingserver.global.security.access.CurrentMember;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Instructor Matching", description = "강사 매칭 API")
public interface InstructorMatchingOfferApiDocs {

    @Operation(
            summary = "강사 현재 노출 매칭 제안 조회",
            description = "현재 로그인한 강사에게 노출된 활성 매칭 제안을 조회합니다.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponse(responseCode = "200", description = "현재 노출 매칭 제안 조회 성공")
    @ApiResponse(responseCode = "400", description = "요청 값 검증 실패 - VALIDATION_FAILED")
    @ApiResponse(responseCode = "401", description = "인증 실패 - UNAUTHENTICATED, AUTH_INVALID_TOKEN, AUTH_TOKEN_EXPIRED")
    @ApiResponse(responseCode = "403", description = "승인된 강사 권한 없음 - FORBIDDEN")
    @ApiResponse(responseCode = "500", description = "서버 내부 오류 - INTERNAL_ERROR")
    ResponseEntity<BaseResponse<InstructorMatchingOffersResponse>> getCurrentOffers(
            @Parameter(hidden = true)
            CurrentMember currentMember,
            @Parameter(description = "페이지 번호. 0부터 시작")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "페이지 크기")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    );

    @Operation(
            summary = "강사 매칭 제안 응답",
            description = "강사가 현재 노출된 매칭 제안을 수락하거나 거절합니다.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponse(responseCode = "200", description = "매칭 제안 응답 반영 성공")
    @ApiResponse(responseCode = "400", description = "요청 값 검증 실패 - VALIDATION_FAILED")
    @ApiResponse(responseCode = "401", description = "인증 실패 - UNAUTHENTICATED, AUTH_INVALID_TOKEN, AUTH_TOKEN_EXPIRED")
    @ApiResponse(responseCode = "403", description = "승인된 강사 권한 없음 - FORBIDDEN")
    @ApiResponse(responseCode = "404", description = "존재하지 않는 매칭 제안 - MATCHING_OFFER_NOT_FOUND")
    @ApiResponse(responseCode = "409", description = "이미 응답했거나 그룹 종료 - MATCHING_OFFER_ALREADY_RESPONDED, MATCHING_GROUP_ALREADY_CLOSED")
    @ApiResponse(responseCode = "500", description = "서버 내부 오류 - INTERNAL_ERROR")
    ResponseEntity<BaseResponse<InstructorMatchingOfferDecisionResponse>> respond(
            @Parameter(hidden = true)
            CurrentMember currentMember,
            @Parameter(description = "매칭 제안 ID")
            @PathVariable Long offerId,
            @Valid @RequestBody RespondInstructorMatchingOfferRequest request
    );
}
