package org.sopt.ssingserver.domain.matching.dev.controller.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.sopt.ssingserver.domain.matching.dev.dto.response.DevMatchingRequestDetailResponse;
import org.sopt.ssingserver.domain.matching.dev.dto.response.DevMatchingRequestListResponse;
import org.sopt.ssingserver.domain.matching.error.MatchingErrorCode;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.sopt.ssingserver.global.response.BaseResponse;
import org.sopt.ssingserver.global.swagger.error.ApiErrorCodes;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Dev", description = "local/dev 전용 개발 데이터 조회 API")
public interface DevMatchingApiDocs {

    @Operation(
            summary = "개발용 매칭 요청 목록 조회",
            description = "local/dev only. 매칭 요청과 계산 상태, 현재 관계 ID, 가능한 동작 key를 읽기 전용으로 조회합니다."
    )
    @ApiResponse(responseCode = "200", description = "개발용 매칭 요청 목록 조회 성공")
    @ApiErrorCodes(
            type = CommonErrorCode.class,
            names = {"VALIDATION_FAILED", "BAD_REQUEST", "INTERNAL_ERROR"}
    )
    ResponseEntity<BaseResponse<DevMatchingRequestListResponse>> getRequests(
            @Parameter(description = "페이지 번호. 0부터 시작")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "페이지 크기. 최대 100")
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size
    );

    @Operation(
            summary = "개발용 매칭 요청 상세 조회",
            description = "local/dev only. 관련 사람과 원본 row ID, 현재 상태, 실행 가능한 동작의 영향 미리보기를 조회합니다. 실제 상태는 변경하지 않습니다."
    )
    @ApiResponse(responseCode = "200", description = "개발용 매칭 요청 상세 조회 성공")
    @ApiErrorCodes(type = CommonErrorCode.class, names = {"BAD_REQUEST", "INTERNAL_ERROR"})
    @ApiErrorCodes(type = MatchingErrorCode.class, names = "MATCHING_REQUEST_NOT_FOUND")
    ResponseEntity<BaseResponse<DevMatchingRequestDetailResponse>> getRequest(
            @Parameter(description = "조회할 매칭 요청 ID")
            @PathVariable Long matchingRequestId
    );
}
