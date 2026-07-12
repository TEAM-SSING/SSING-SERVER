package org.sopt.ssingserver.domain.lesson.controller.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.sopt.ssingserver.domain.auth.error.AuthErrorCode;
import org.sopt.ssingserver.domain.lesson.dto.request.LessonCancellationRequest;
import org.sopt.ssingserver.domain.lesson.dto.response.ConsumerLessonDetailResponse;
import org.sopt.ssingserver.domain.lesson.dto.response.InstructorLessonDetailResponse;
import org.sopt.ssingserver.domain.lesson.dto.response.LessonCancellationResponse;
import org.sopt.ssingserver.domain.lesson.dto.response.LessonCompletionResponse;
import org.sopt.ssingserver.domain.lesson.dto.response.LessonStartConfirmationResponse;
import org.sopt.ssingserver.domain.lesson.error.LessonErrorCode;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.sopt.ssingserver.global.response.BaseResponse;
import org.sopt.ssingserver.global.security.access.CurrentMember;
import org.sopt.ssingserver.global.swagger.error.ApiErrorCodes;
import org.sopt.ssingserver.global.swagger.success.ApiSuccessExamples;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Lesson", description = "강습 API")
public interface LessonApiDocs {

    @Operation(
            summary = "소비자 강습 상세 조회",
            description = "회원 앱에서 강습 상태에 따른 강습 상세 정보를 조회합니다.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponse(responseCode = "200", description = "강습 상세 조회 성공")
    @ApiSuccessExamples(LessonApiExamples.ConsumerDetail.class)
    @ApiErrorCodes(
            type = CommonErrorCode.class,
            names = {"UNAUTHENTICATED", "FORBIDDEN", "INTERNAL_ERROR"}
    )
    @ApiErrorCodes(
            type = AuthErrorCode.class,
            names = {"AUTH_INVALID_TOKEN", "AUTH_TOKEN_EXPIRED"}
    )
    @ApiErrorCodes(
            type = LessonErrorCode.class,
            names = {
                    "LESSON_NOT_FOUND",
                    "LESSON_FORBIDDEN",
                    "LESSON_PRICE_NOT_FOUND",
                    "LESSON_CANCELLATION_NOT_FOUND",
                    "LESSON_INVALID_STATE"
            }
    )
    ResponseEntity<BaseResponse<ConsumerLessonDetailResponse>> getConsumerLessonDetail(
            @Parameter(hidden = true)
            CurrentMember currentMember,
            @Parameter(description = "강습 ID")
            @PathVariable Long lessonId
    );

    @Operation(
            summary = "강사 강습 상세 조회",
            description = "강사 앱에서 강습 상태에 따른 강습 상세 정보를 조회합니다.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponse(responseCode = "200", description = "강습 상세 조회 성공")
    @ApiSuccessExamples(LessonApiExamples.InstructorDetail.class)
    @ApiErrorCodes(
            type = CommonErrorCode.class,
            names = {"UNAUTHENTICATED", "FORBIDDEN", "INTERNAL_ERROR"}
    )
    @ApiErrorCodes(
            type = AuthErrorCode.class,
            names = {"AUTH_INVALID_TOKEN", "AUTH_TOKEN_EXPIRED"}
    )
    @ApiErrorCodes(
            type = LessonErrorCode.class,
            names = {
                    "LESSON_NOT_FOUND",
                    "LESSON_PRICE_NOT_FOUND",
                    "LESSON_CANCELLATION_NOT_FOUND",
                    "LESSON_INVALID_STATE"
            }
    )
    ResponseEntity<BaseResponse<InstructorLessonDetailResponse>> getInstructorLessonDetail(
            @Parameter(hidden = true)
            CurrentMember currentMember,
            @Parameter(description = "강습 ID")
            @PathVariable Long lessonId
    );

    @Operation(
            summary = "강습 시작 확인",
            description = "강사 또는 강습을 구성하는 매칭 요청의 대표 소비자가 강습 시작 전 확인을 완료합니다.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponse(responseCode = "200", description = "강습 시작 확인 성공")
    @ApiSuccessExamples(LessonApiExamples.StartConfirmation.class)
    @ApiErrorCodes(
            type = CommonErrorCode.class,
            names = {"UNAUTHENTICATED", "FORBIDDEN", "INTERNAL_ERROR"}
    )
    @ApiErrorCodes(
            type = AuthErrorCode.class,
            names = {"AUTH_INVALID_TOKEN", "AUTH_TOKEN_EXPIRED"}
    )
    @ApiErrorCodes(
            type = LessonErrorCode.class,
            names = {"LESSON_NOT_FOUND", "LESSON_START_NOT_ALLOWED", "LESSON_INVALID_STATE"}
    )
    ResponseEntity<BaseResponse<LessonStartConfirmationResponse>> confirmLessonStart(
            @Parameter(hidden = true)
            CurrentMember currentMember,
            @Parameter(description = "강습 ID")
            @PathVariable Long lessonId
    );

    @Operation(
            summary = "강습 종료",
            description = "진행 중인 강습을 담당 강사 또는 대표 소비자 한 명의 요청으로 종료합니다.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponse(responseCode = "200", description = "강습 종료 성공")
    @ApiErrorCodes(
            type = CommonErrorCode.class,
            names = {"UNAUTHENTICATED", "FORBIDDEN", "INTERNAL_ERROR"}
    )
    @ApiErrorCodes(
            type = AuthErrorCode.class,
            names = {"AUTH_INVALID_TOKEN", "AUTH_TOKEN_EXPIRED"}
    )
    @ApiErrorCodes(
            type = LessonErrorCode.class,
            names = {"LESSON_NOT_FOUND", "LESSON_COMPLETE_NOT_ALLOWED"}
    )
    ResponseEntity<BaseResponse<LessonCompletionResponse>> completeLesson(
            @Parameter(hidden = true)
            CurrentMember currentMember,
            @Parameter(description = "강습 ID")
            @PathVariable Long lessonId
    );

    @Operation(
            summary = "강습 취소",
            description = "강습을 취소합니다.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponse(responseCode = "200", description = "강습 취소 성공")
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
            type = LessonErrorCode.class,
            names = {"LESSON_NOT_FOUND", "LESSON_CANCEL_NOT_ALLOWED"}
    )
    ResponseEntity<BaseResponse<LessonCancellationResponse>> cancelLesson(
            @Parameter(hidden = true)
            CurrentMember currentMember,
            @Parameter(description = "강습 ID")
            @PathVariable Long lessonId,
            @Valid @RequestBody LessonCancellationRequest request
    );
}
