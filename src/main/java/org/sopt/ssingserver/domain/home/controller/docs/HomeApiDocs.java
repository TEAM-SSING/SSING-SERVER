package org.sopt.ssingserver.domain.home.controller.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.sopt.ssingserver.domain.auth.error.AuthErrorCode;
import org.sopt.ssingserver.domain.home.dto.response.ConsumerHomeResponse;
import org.sopt.ssingserver.domain.home.dto.response.InstructorHomeResponse;
import org.sopt.ssingserver.domain.home.error.HomeErrorCode;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.sopt.ssingserver.global.response.BaseResponse;
import org.sopt.ssingserver.global.security.access.CurrentMember;
import org.sopt.ssingserver.global.swagger.error.ApiErrorCodes;
import org.springframework.http.ResponseEntity;

@Tag(name = "Home", description = "홈 화면 API")
public interface HomeApiDocs {

    @Operation(
            summary = "소비자 홈 화면 조회",
            description = "소비자 앱 홈화면에 표시할 강습 정보를 조회합니다.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponse(responseCode = "200", description = "회원 홈화면 조회 성공")
    @ApiErrorCodes(
            type = CommonErrorCode.class,
            names = {"UNAUTHENTICATED", "FORBIDDEN", "INTERNAL_ERROR"}
    )
    @ApiErrorCodes(
            type = AuthErrorCode.class,
            names = {"AUTH_INVALID_TOKEN", "AUTH_TOKEN_EXPIRED"}
    )
    ResponseEntity<BaseResponse<ConsumerHomeResponse>> getConsumerHome(
            @Parameter(hidden = true)
            CurrentMember currentMember
    );

    @Operation(
            summary = "강사 홈 화면 조회",
            description = "강사 앱 홈화면에 표시할 매칭/강습 정보를 조회합니다. "
                    + "CONFIRMED/IN_PROGRESS 강습 카드는 원본 offerId와 lessonId를 함께 반환합니다.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponse(responseCode = "200", description = "강사 홈화면 조회 성공")
    @ApiErrorCodes(
            type = CommonErrorCode.class,
            names = {"UNAUTHENTICATED", "FORBIDDEN", "NOT_FOUND", "INTERNAL_ERROR"}
    )
    @ApiErrorCodes(
            type = AuthErrorCode.class,
            names = {"AUTH_INVALID_TOKEN", "AUTH_TOKEN_EXPIRED"}
    )
    @ApiErrorCodes(
            type = HomeErrorCode.class,
            names = {
                    "INSTRUCTOR_HOME_UNSUPPORTED_DISPLAY_STATUS",
                    "INSTRUCTOR_HOME_GROUP_ITEM_NOT_FOUND"
            }
    )
    ResponseEntity<BaseResponse<InstructorHomeResponse>> getInstructorHome(
            @Parameter(hidden = true)
            CurrentMember currentMember
    );
}
