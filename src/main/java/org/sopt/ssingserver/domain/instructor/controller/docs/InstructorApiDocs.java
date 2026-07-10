package org.sopt.ssingserver.domain.instructor.controller.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.sopt.ssingserver.domain.instructor.dto.request.InstructorMatchingExposureRequest;
import org.sopt.ssingserver.domain.instructor.dto.response.InstructorMatchingExposureCancelResponse;
import org.sopt.ssingserver.domain.instructor.dto.response.InstructorMatchingExposureConditionsResponse;
import org.sopt.ssingserver.domain.instructor.dto.response.InstructorMatchingExposureResponse;
import org.sopt.ssingserver.global.response.BaseResponse;
import org.sopt.ssingserver.global.response.docs.CommonErrorResponseDocs;
import org.sopt.ssingserver.global.response.docs.ValidationErrorResponseDocs;
import org.sopt.ssingserver.global.security.access.CurrentMember;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Instructor Matching", description = "강사 매칭 API")
public interface InstructorApiDocs {

    @Operation(
            summary = "강사 즉시노출 조건 화면 조회",
            description = "활동 리조트, 자격증 기준 선택 가능 종목, 시간 옵션과 저장된 조건을 조회합니다.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponse(
            responseCode = "200",
            description = "즉시노출 조건 화면 조회 성공",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(
                            implementation = InstructorMatchingExposureConditionsSuccessResponseDocs.class
                    )
            )
    )
    @ApiResponse(
            responseCode = "401",
            description = "인증 실패",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = CommonErrorResponseDocs.class),
                    examples = {
                            @ExampleObject(
                                    name = "UNAUTHENTICATED",
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
                                    name = "AUTH_INVALID_TOKEN",
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
                                    name = "AUTH_TOKEN_EXPIRED",
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
            description = "승인된 강사 권한 없음",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = CommonErrorResponseDocs.class),
                    examples = @ExampleObject(
                            name = "FORBIDDEN",
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
            description = "활동 리조트 미등록",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = CommonErrorResponseDocs.class),
                    examples = @ExampleObject(
                            name = "INSTRUCTOR_RESORT_NOT_SET",
                            value = """
                                    {
                                      "success": false,
                                      "code": "INSTRUCTOR_RESORT_NOT_SET",
                                      "message": "활동 리조트가 등록되지 않았습니다.",
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
                            name = "INTERNAL_ERROR",
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
    ResponseEntity<BaseResponse<InstructorMatchingExposureConditionsResponse>> getExposureConditions(
            @Parameter(hidden = true)
            CurrentMember currentMember
    );

    @Operation(
            summary = "강사 즉시 매칭 조건 저장 및 시작",
            description = "강사가 즉시 매칭에 노출될 조건을 저장하고 매칭을 시작합니다.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponse(
            responseCode = "200",
            description = "즉시 매칭 조건 저장 및 매칭 시작 성공",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(
                            implementation = InstructorMatchingExposureStartSuccessResponseDocs.class
                    )
            )
    )
    @ApiResponse(
            responseCode = "400",
            description = "요청 값 또는 자격 종목 검증 실패",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(oneOf = {
                            ValidationErrorResponseDocs.class,
                            CommonErrorResponseDocs.class
                    }),
                    examples = {
                            @ExampleObject(
                                    name = "VALIDATION_FAILED",
                                    value = """
                                            {
                                              "success": false,
                                              "code": "VALIDATION_FAILED",
                                              "message": "요청 값 검증에 실패했습니다.",
                                              "errors": {
                                                "sport": "보유 자격증으로 선택할 수 없는 종목입니다."
                                              },
                                              "requestId": "req_abc123"
                                            }
                                            """
                            ),
                            @ExampleObject(
                                    name = "BAD_REQUEST",
                                    value = """
                                            {
                                              "success": false,
                                              "code": "BAD_REQUEST",
                                              "message": "잘못된 요청입니다.",
                                              "requestId": "req_abc123"
                                            }
                                            """
                            )
                    }
            )
    )
    @ApiResponse(
            responseCode = "401",
            description = "인증 실패",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = CommonErrorResponseDocs.class),
                    examples = {
                            @ExampleObject(
                                    name = "UNAUTHENTICATED",
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
                                    name = "AUTH_INVALID_TOKEN",
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
                                    name = "AUTH_TOKEN_EXPIRED",
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
            description = "승인된 강사 권한 없음",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = CommonErrorResponseDocs.class),
                    examples = @ExampleObject(
                            name = "FORBIDDEN",
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
            description = "활동 리조트 미등록",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = CommonErrorResponseDocs.class),
                    examples = @ExampleObject(
                            name = "INSTRUCTOR_RESORT_NOT_SET",
                            value = """
                                    {
                                      "success": false,
                                      "code": "INSTRUCTOR_RESORT_NOT_SET",
                                      "message": "활동 리조트가 등록되지 않았습니다.",
                                      "requestId": "req_abc123"
                                    }
                                    """
                    )
            )
    )
    @ApiResponse(
            responseCode = "409",
            description = "현재 진행 중인 강습 존재",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = CommonErrorResponseDocs.class),
                    examples = @ExampleObject(
                            name = "ACTIVE_LESSON_EXISTS",
                            value = """
                                    {
                                      "success": false,
                                      "code": "ACTIVE_LESSON_EXISTS",
                                      "message": "현재 진행 중인 강습이 있어 즉시노출을 시작할 수 없습니다.",
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
                            name = "INTERNAL_ERROR",
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
    ResponseEntity<BaseResponse<InstructorMatchingExposureCancelResponse>> cancelExposure(
            @Parameter(hidden = true)
            CurrentMember currentMember
    );
}
