package org.sopt.ssingserver.domain.matching.controller.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.sopt.ssingserver.domain.auth.error.AuthErrorCode;
import org.sopt.ssingserver.domain.matching.dto.request.RespondInstructorMatchingOfferRequest;
import org.sopt.ssingserver.domain.matching.dto.response.InstructorMatchingOfferDecisionResponse;
import org.sopt.ssingserver.domain.matching.dto.response.InstructorMatchingOfferDetailResponse;
import org.sopt.ssingserver.domain.matching.dto.response.InstructorMatchingOffersResponse;
import org.sopt.ssingserver.domain.matching.error.MatchingErrorCode;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.sopt.ssingserver.global.response.BaseResponse;
import org.sopt.ssingserver.global.security.access.CurrentMember;
import org.sopt.ssingserver.global.swagger.error.ApiErrorCodes;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Instructor Matching", description = "강사 매칭 API")
public interface InstructorMatchingOfferApiDocs {

    @Operation(
            summary = "강사 현재 노출 매칭 제안 조회",
            description = "현재 로그인한 강사에게 노출된 활성 매칭 제안과 제안 생성 시점에 고정된 강습비, 리조트 패스비, 최종 결제 금액을 조회합니다.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponse(responseCode = "200", description = "현재 노출 매칭 제안 조회 성공")
    @ApiErrorCodes(
            type = CommonErrorCode.class,
            names = {
                    "VALIDATION_FAILED",
                    "BAD_REQUEST",
                    "UNAUTHENTICATED",
                    "FORBIDDEN",
                    "INTERNAL_ERROR"
            }
    )
    @ApiErrorCodes(
            type = AuthErrorCode.class,
            names = {"AUTH_INVALID_TOKEN", "AUTH_TOKEN_EXPIRED"}
    )
    ResponseEntity<BaseResponse<InstructorMatchingOffersResponse>> getCurrentOffers(
            @Parameter(hidden = true)
            CurrentMember currentMember,
            @Parameter(description = "페이지 번호. 0부터 시작")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "페이지 크기")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    );

    @Operation(
            summary = "강사 활성 매칭 제안 상세 조회",
            description = "강사 홈의 offerId로 현재 활성 제안 또는 수락 후 진행 중인 매칭 상태를 복구합니다. "
                    + "I07 구성에 필요한 그룹 전체 강습생의 나이와 성별을 함께 반환합니다. "
                    + "제안이 이미 종료되거나 강습으로 확정된 경우에는 홈을 다시 조회합니다.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponse(responseCode = "200", description = "강사 활성 매칭 제안 상세 조회 성공")
    @ApiErrorCodes(
            type = CommonErrorCode.class,
            names = {
                    "BAD_REQUEST",
                    "UNAUTHENTICATED",
                    "FORBIDDEN",
                    "INTERNAL_ERROR"
            }
    )
    @ApiErrorCodes(
            type = AuthErrorCode.class,
            names = {"AUTH_INVALID_TOKEN", "AUTH_TOKEN_EXPIRED"}
    )
    @ApiErrorCodes(
            type = MatchingErrorCode.class,
            names = {"MATCHING_OFFER_NOT_FOUND"}
    )
    ResponseEntity<BaseResponse<InstructorMatchingOfferDetailResponse>> getOfferDetail(
            @Parameter(hidden = true)
            CurrentMember currentMember,
            @Parameter(description = "매칭 제안 ID")
            @PathVariable Long offerId
    );

    @Operation(
            summary = "강사 매칭 제안 응답",
            description = "강사가 현재 노출된 매칭 제안을 수락하거나 거절합니다. "
                    + "수락 후에는 이 API로 철회하거나 거절로 변경할 수 없습니다.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponse(responseCode = "200", description = "매칭 제안 응답 반영 성공")
    @ApiErrorCodes(
            type = CommonErrorCode.class,
            names = {
                    "VALIDATION_FAILED",
                    "BAD_REQUEST",
                    "UNAUTHENTICATED",
                    "FORBIDDEN",
                    "INTERNAL_ERROR"
            }
    )
    @ApiErrorCodes(
            type = AuthErrorCode.class,
            names = {"AUTH_INVALID_TOKEN", "AUTH_TOKEN_EXPIRED"}
    )
    @ApiErrorCodes(
            type = MatchingErrorCode.class,
            names = {
                    "MATCHING_OFFER_NOT_FOUND",
                    "MATCHING_OFFER_ALREADY_RESPONDED",
                    "MATCHING_GROUP_ALREADY_CLOSED"
            }
    )
    ResponseEntity<BaseResponse<InstructorMatchingOfferDecisionResponse>> respond(
            @Parameter(hidden = true)
            CurrentMember currentMember,
            @Parameter(description = "매칭 제안 ID")
            @PathVariable Long offerId,
            @Valid @RequestBody RespondInstructorMatchingOfferRequest request
    );
}
