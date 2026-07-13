package org.sopt.ssingserver.domain.instructor.controller.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.sopt.ssingserver.domain.auth.error.AuthErrorCode;
import org.sopt.ssingserver.domain.instructor.dto.request.InstructorMatchingExposureRequest;
import org.sopt.ssingserver.domain.instructor.dto.response.InstructorMatchingExposureCancelResponse;
import org.sopt.ssingserver.domain.instructor.dto.response.InstructorMatchingExposureConditionsResponse;
import org.sopt.ssingserver.domain.instructor.dto.response.InstructorMatchingExposureResponse;
import org.sopt.ssingserver.domain.instructor.error.InstructorErrorCode;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.sopt.ssingserver.global.response.BaseResponse;
import org.sopt.ssingserver.global.security.access.CurrentMember;
import org.sopt.ssingserver.global.swagger.error.ApiErrorCodes;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Instructor Matching", description = "강사 매칭 API")
public interface InstructorApiDocs {

    @Operation(
            summary = "강사 즉시노출 조건 화면 조회",
            description = "활동 리조트와 자격증 기준 선택 가능 종목을 조회합니다.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponse(responseCode = "200", description = "즉시노출 조건 화면 조회 성공")
    @ApiErrorCodes(
            type = CommonErrorCode.class,
            names = {"UNAUTHENTICATED", "FORBIDDEN", "INTERNAL_ERROR"}
    )
    @ApiErrorCodes(
            type = AuthErrorCode.class,
            names = {"AUTH_INVALID_TOKEN", "AUTH_TOKEN_EXPIRED"}
    )
    @ApiErrorCodes(type = InstructorErrorCode.class, names = "INSTRUCTOR_RESORT_NOT_SET")
    ResponseEntity<BaseResponse<InstructorMatchingExposureConditionsResponse>> getExposureConditions(
            @Parameter(hidden = true)
            CurrentMember currentMember
    );

    @Operation(
            summary = "강사 즉시 매칭 조건 저장 및 시작",
            description = "강사가 즉시 매칭에 노출될 조건을 저장하고 매칭을 시작합니다.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponse(responseCode = "200", description = "즉시 매칭 조건 저장 및 매칭 시작 성공")
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
            type = InstructorErrorCode.class,
            names = {"INSTRUCTOR_RESORT_NOT_SET", "ACTIVE_LESSON_EXISTS"}
    )
    ResponseEntity<BaseResponse<InstructorMatchingExposureResponse>> startExposure(
            @Parameter(hidden = true)
            CurrentMember currentMember,
            @Valid @RequestBody InstructorMatchingExposureRequest request
    );

    @Operation(
            summary = "강사 즉시 매칭 노출 중단",
            description = "강사의 즉시 매칭 노출을 중단합니다.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponse(responseCode = "200", description = "즉시 매칭 노출 중단 성공")
    @ApiErrorCodes(
            type = CommonErrorCode.class,
            names = {"UNAUTHENTICATED", "FORBIDDEN", "NOT_FOUND", "INTERNAL_ERROR"}
    )
    @ApiErrorCodes(
            type = AuthErrorCode.class,
            names = {"AUTH_INVALID_TOKEN", "AUTH_TOKEN_EXPIRED"}
    )
    ResponseEntity<BaseResponse<InstructorMatchingExposureCancelResponse>> cancelExposure(
            @Parameter(hidden = true)
            CurrentMember currentMember
    );
}
