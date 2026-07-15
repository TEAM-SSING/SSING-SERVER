package org.sopt.ssingserver.domain.matching.controller.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.sopt.ssingserver.domain.auth.error.AuthErrorCode;
import org.sopt.ssingserver.domain.instructor.error.InstructorErrorCode;
import org.sopt.ssingserver.domain.matching.dto.request.RespondInstructorMatchingOfferRequest;
import org.sopt.ssingserver.domain.matching.dto.response.InstructorMatchingOfferDecisionResponse;
import org.sopt.ssingserver.domain.matching.dto.response.InstructorMatchingOfferDetailResponse;
import org.sopt.ssingserver.domain.matching.dto.response.InstructorMatchingOffersResponse;
import org.sopt.ssingserver.domain.matching.error.MatchingErrorCode;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.sopt.ssingserver.global.response.BaseResponse;
import org.sopt.ssingserver.global.security.access.CurrentMember;
import org.sopt.ssingserver.global.swagger.error.ApiErrorCodes;
import org.sopt.ssingserver.global.swagger.success.ApiSuccessExamples;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Instructor Matching", description = "강사 매칭 API")
public interface InstructorMatchingOfferApiDocs {

    @Operation(
            summary = "강사 매칭 대기 화면 및 새 제안 재확인",
            description = "홈의 MATCHING 카드에 offerId가 없을 때 호출합니다. 홈 조회 뒤 생긴 활성 실시간 제안이 있으면 "
                    + "상세 조회용 offerId를, 없으면 null을 반환합니다. matchingSetting으로 저장된 매칭 대기 조건을 복구하며, "
                    + "제안 상세와 예상 가격은 이 응답에 포함하지 않습니다.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponse(responseCode = "200", description = "매칭 대기 조건 및 현재 제안 조회 성공")
    @ApiSuccessExamples(MatchingApiExamples.InstructorCurrentOffers.class)
    @ApiErrorCodes(
            type = CommonErrorCode.class,
            names = {
                    "UNAUTHENTICATED",
                    "FORBIDDEN",
                    "NOT_FOUND",
                    "INTERNAL_ERROR"
            }
    )
    @ApiErrorCodes(
            type = AuthErrorCode.class,
            names = {"AUTH_INVALID_TOKEN", "AUTH_TOKEN_EXPIRED"}
    )
    @ApiErrorCodes(
            type = InstructorErrorCode.class,
            names = {"INSTRUCTOR_RESORT_NOT_SET"}
    )
    @ApiErrorCodes(
            type = MatchingErrorCode.class,
            names = {"MATCHING_NOT_ACTIVE"}
    )
    ResponseEntity<BaseResponse<InstructorMatchingOffersResponse>> getCurrentOffers(
            @Parameter(hidden = true)
            CurrentMember currentMember
    );

    @Operation(
            summary = "강사 매칭 제안 상세 복구 조회",
            description = "강사 홈 또는 ID 없는 매칭 재확인 API에서 받은 offerId로 제안 상세를 복구합니다. "
                    + "복구 가능하면 AVAILABLE과 상세를 반환하고, 본인 소유지만 이미 종료된 제안이면 409를 반환합니다. "
                    + "제안이 없거나 다른 강사 소유이면 404를 반환합니다.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponse(responseCode = "200", description = "강사 매칭 제안 상세 복구 조회 성공(AVAILABLE)")
    @ApiSuccessExamples(MatchingApiExamples.InstructorOfferDetail.class)
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
            names = {"MATCHING_OFFER_NOT_FOUND", "MATCHING_NOT_ACTIVE"}
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
