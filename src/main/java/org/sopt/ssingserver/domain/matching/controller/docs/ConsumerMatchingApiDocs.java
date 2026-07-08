package org.sopt.ssingserver.domain.matching.controller.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.sopt.ssingserver.domain.matching.dto.request.CreateConsumerMatchingRequest;
import org.sopt.ssingserver.domain.matching.dto.response.ConsumerMatchingCancellationResponse;
import org.sopt.ssingserver.domain.matching.dto.response.ConsumerMatchingRequestCreateResponse;
import org.sopt.ssingserver.global.response.BaseResponse;
import org.sopt.ssingserver.global.security.access.CurrentMember;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Consumer Matching", description = "소비자 매칭 API")
public interface ConsumerMatchingApiDocs {

    @Operation(
            summary = "소비자 매칭 요청 생성",
            description = "회원이 입력한 조건을 기반으로 즉시 매칭 요청을 생성합니다.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponse(responseCode = "201", description = "매칭 요청 생성 성공")
    ResponseEntity<BaseResponse<ConsumerMatchingRequestCreateResponse>> createMatchingRequest(
            @Parameter(hidden = true)
            CurrentMember currentMember,
            @Valid @RequestBody CreateConsumerMatchingRequest request
    );

    @Operation(
            summary = "소비자 매칭 요청 중지",
            description = "진행 중인 매칭 요청을 완전 취소하고 관련 활성 그룹, 제안, 결제 요청을 종료합니다.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponse(responseCode = "200", description = "매칭 요청 중지 성공")
    @ApiResponse(responseCode = "401", description = "인증 실패")
    @ApiResponse(responseCode = "403", description = "본인 소유의 매칭 요청이 아님")
    @ApiResponse(responseCode = "404", description = "존재하지 않는 매칭 요청")
    @ApiResponse(responseCode = "409", description = "취소 가능한 매칭 요청 상태가 아님")
    @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    ResponseEntity<BaseResponse<ConsumerMatchingCancellationResponse>> cancelMatchingRequest(
            @Parameter(hidden = true)
            CurrentMember currentMember,
            @Parameter(description = "매칭 요청 ID")
            @PathVariable Long matchingRequestId
    );
}
