package org.sopt.ssingserver.domain.matching.controller.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.sopt.ssingserver.domain.matching.dto.request.ConsumerMatchingRequestCreateRequest;
import org.sopt.ssingserver.domain.matching.dto.response.ConsumerMatchingRequestCreateResponse;
import org.sopt.ssingserver.global.response.BaseResponse;
import org.sopt.ssingserver.global.security.access.CurrentMember;
import org.springframework.http.ResponseEntity;
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
            @Valid @RequestBody ConsumerMatchingRequestCreateRequest request
    );
}
