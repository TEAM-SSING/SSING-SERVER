package org.sopt.ssingserver.domain.auth.dev.controller.docs;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.sopt.ssingserver.domain.auth.dev.dto.request.CreateDevPersonaRequest;
import org.sopt.ssingserver.domain.auth.dev.dto.request.DevAuthTokenRequest;
import org.sopt.ssingserver.domain.auth.dev.dto.response.CreateDevPersonaResponse;
import org.sopt.ssingserver.domain.auth.dev.dto.response.DevAuthTokenResponse;
import org.sopt.ssingserver.domain.auth.dev.dto.response.DevPersonaListResponse;
import org.sopt.ssingserver.domain.auth.dev.error.DevAuthErrorCode;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.sopt.ssingserver.global.response.BaseResponse;
import org.sopt.ssingserver.global.swagger.error.ApiErrorCodes;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Dev Auth", description = "local/dev 전용 인증 도구 API")
public interface DevAuthApiDocs {

    @Operation(
            summary = "개발용 persona 목록 조회",
            description = "local/dev only. 합성 persona와 현재 회원 상태를 조회합니다."
    )
    @ApiResponse(responseCode = "200", description = "개발용 persona 목록 조회 성공")
    @ApiErrorCodes(type = CommonErrorCode.class, names = "INTERNAL_ERROR")
    ResponseEntity<BaseResponse<DevPersonaListResponse>> getPersonas();

    @Hidden
    @Operation(
            summary = "개발용 persona 생성",
            description = "local/dev only. 선택한 합성 상태로 persona를 생성합니다."
    )
    @ApiResponse(responseCode = "201", description = "개발용 persona 생성 성공")
    @ApiErrorCodes(
            type = CommonErrorCode.class,
            names = {"VALIDATION_FAILED", "BAD_REQUEST", "INTERNAL_ERROR"}
    )
    @ApiErrorCodes(
            type = DevAuthErrorCode.class,
            names = {"DEV_PERSONA_INVALID_TEMPLATE", "DEV_PERSONA_ALREADY_EXISTS"}
    )
    ResponseEntity<BaseResponse<CreateDevPersonaResponse>> createPersona(
            @Valid @RequestBody CreateDevPersonaRequest request
    );

    @Hidden
    @Operation(
            summary = "개발용 토큰 발급",
            description = "local/dev only. 합성 persona의 Access Token과 Refresh Token을 발급합니다."
    )
    @ApiResponse(responseCode = "200", description = "개발용 토큰 발급 성공")
    @ApiErrorCodes(
            type = CommonErrorCode.class,
            names = {"VALIDATION_FAILED", "BAD_REQUEST", "INTERNAL_ERROR"}
    )
    @ApiErrorCodes(type = DevAuthErrorCode.class, names = "DEV_PERSONA_NOT_FOUND")
    ResponseEntity<BaseResponse<DevAuthTokenResponse>> issueToken(
            @Valid @RequestBody DevAuthTokenRequest request
    );
}
