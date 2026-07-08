package org.sopt.ssingserver.domain.home.controller.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.sopt.ssingserver.domain.home.dto.response.ConsumerHomeResponse;
import org.sopt.ssingserver.global.response.BaseResponse;
import org.sopt.ssingserver.global.security.access.CurrentMember;
import org.springframework.http.ResponseEntity;

@Tag(name = "Home", description = "홈 화면 API")
public interface HomeApiDocs {

    @Operation(
            summary = "소비자 홈 화면 조회",
            description = "소비자 앱 홈화면에 표시할 강습 정보를 조회합니다.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponse(responseCode = "200", description = "회원 홈화면 조회 성공")
    ResponseEntity<BaseResponse<ConsumerHomeResponse>> getHome(
            @Parameter(hidden = true)
            CurrentMember currentMember
    );
}
