package org.sopt.ssingserver.domain.auth.controller.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.sopt.ssingserver.domain.auth.dto.request.KakaoLoginRequest;
import org.sopt.ssingserver.domain.auth.dto.response.ConsumerKakaoLoginResponse;
import org.sopt.ssingserver.domain.auth.error.AuthErrorCode;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.sopt.ssingserver.global.response.BaseResponse;
import org.sopt.ssingserver.global.swagger.error.ApiErrorCodes;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Consumer Auth", description = "소비자 인증 API")
public interface ConsumerAuthApiDocs {

    @Operation(
            summary = "소비자 카카오 로그인",
            description = "카카오 Access Token을 검증하고 소비자용 SSING 토큰을 발급합니다."
    )
    @ApiResponse(responseCode = "200", description = "소비자 카카오 로그인 성공")
    @ApiErrorCodes(
            type = CommonErrorCode.class,
            names = {
                    "VALIDATION_FAILED",
                    "BAD_REQUEST",
                    "FORBIDDEN",
                    "EXTERNAL_SERVICE_UNAVAILABLE",
                    "INTERNAL_ERROR"
            }
    )
    @ApiErrorCodes(type = AuthErrorCode.class, names = "AUTH_INVALID_KAKAO_TOKEN")
    ResponseEntity<BaseResponse<ConsumerKakaoLoginResponse>> loginWithKakao(
            @Valid @RequestBody KakaoLoginRequest request
    );
}
