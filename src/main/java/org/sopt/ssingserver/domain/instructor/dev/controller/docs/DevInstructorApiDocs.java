package org.sopt.ssingserver.domain.instructor.dev.controller.docs;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.sopt.ssingserver.domain.instructor.dev.dto.request.ExecuteDevInstructorActionRequest;
import org.sopt.ssingserver.domain.instructor.dev.dto.response.DevInstructorActionExecutionResponse;
import org.sopt.ssingserver.domain.instructor.dev.dto.response.DevInstructorMemberListResponse;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.sopt.ssingserver.global.response.BaseResponse;
import org.sopt.ssingserver.global.swagger.error.ApiErrorCodes;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Dev", description = "local/dev 전용 개발 데이터 API")
public interface DevInstructorApiDocs {

    @Operation(
            summary = "개발용 실제 Kakao 회원 강사 상태 조회",
            description = "local/dev 및 기능 플래그 전용. 실제 Kakao 회원의 강사 상태와 내부 ID, 현재 가능한 동작을 조회합니다."
    )
    @ApiResponse(responseCode = "200", description = "실제 Kakao 회원 강사 상태 조회 성공")
    @ApiErrorCodes(
            type = CommonErrorCode.class,
            names = {"VALIDATION_FAILED", "BAD_REQUEST", "INTERNAL_ERROR"}
    )
    ResponseEntity<BaseResponse<DevInstructorMemberListResponse>> getMembers(
            @Parameter(description = "페이지 번호. 0부터 시작")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "페이지 크기. 최대 200")
            @RequestParam(defaultValue = "100") @Min(1) @Max(200) int size
    );

    // 실제 회원 상태를 바꾸므로 별도 접근 경계가 생기기 전까지 공개 Swagger 계약에서 숨긴다.
    @Hidden
    ResponseEntity<BaseResponse<DevInstructorActionExecutionResponse>> executeAction(
            @PathVariable Long memberId,
            @Valid @RequestBody ExecuteDevInstructorActionRequest request
    );
}
