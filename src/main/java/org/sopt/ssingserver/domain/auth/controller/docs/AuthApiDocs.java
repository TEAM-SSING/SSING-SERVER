package org.sopt.ssingserver.domain.auth.controller.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.sopt.ssingserver.domain.auth.dto.request.AuthLogoutRequest;
import org.sopt.ssingserver.domain.auth.dto.request.AuthRefreshRequest;
import org.sopt.ssingserver.domain.auth.dto.response.AuthRefreshResponse;
import org.sopt.ssingserver.domain.auth.error.AuthErrorCode;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.sopt.ssingserver.global.response.BaseResponse;
import org.sopt.ssingserver.global.swagger.error.ApiErrorCodes;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Auth", description = "공통 인증 API")
public interface AuthApiDocs {

    @Operation(
            summary = "Access Token 재발급",
            description = "유효한 Refresh Token으로 새 Access Token을 발급합니다."
    )
    @ApiResponse(responseCode = "200", description = "Access Token 재발급 성공")
    @ApiErrorCodes(
            type = CommonErrorCode.class,
            names = {"VALIDATION_FAILED", "BAD_REQUEST", "FORBIDDEN", "INTERNAL_ERROR"}
    )
    @ApiErrorCodes(
            type = AuthErrorCode.class,
            names = {"AUTH_INVALID_TOKEN", "AUTH_TOKEN_EXPIRED"}
    )
    ResponseEntity<BaseResponse<AuthRefreshResponse>> refreshAccessToken(
            @Valid @RequestBody AuthRefreshRequest request
    );

    @Operation(
            summary = "로그아웃",
            description = "Refresh Token을 폐기해 로그아웃 처리합니다."
    )
    @ApiResponse(responseCode = "204", description = "로그아웃 성공")
    @ApiErrorCodes(
            type = CommonErrorCode.class,
            names = {"VALIDATION_FAILED", "BAD_REQUEST", "INTERNAL_ERROR"}
    )
    @ApiErrorCodes(type = AuthErrorCode.class, names = "AUTH_INVALID_TOKEN")
    ResponseEntity<Void> logout(
            @Valid @RequestBody AuthLogoutRequest request
    );
}
