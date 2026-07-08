package org.sopt.ssingserver.domain.matching.controller.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.sopt.ssingserver.domain.matching.dto.response.ConsumerMatchingStatusResponse;
import org.sopt.ssingserver.global.response.BaseResponse;
import org.sopt.ssingserver.global.security.access.CurrentMember;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;

@Tag(name = "Matching", description = "즉시 매칭 API")
public interface MatchingApiDocs {

    @Operation(
            summary = "소비자 매칭 진행 상태 조회",
            description = "소비자가 생성한 즉시 매칭 요청의 현재 진행 상태를 조회합니다.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    // TODO: Swagger 문서화 기준 확정 후 4xx/5xx 실패 응답을 일괄 반영
    @ApiResponse(responseCode = "200", description = "매칭 진행 상태 조회 성공")
    ResponseEntity<BaseResponse<ConsumerMatchingStatusResponse>> getStatus(
            @Parameter(hidden = true)
            CurrentMember currentMember,
            @Parameter(description = "매칭 요청 ID")
            @PathVariable Long matchingRequestId
    );
}
