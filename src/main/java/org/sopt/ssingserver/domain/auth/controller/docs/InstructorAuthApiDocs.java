package org.sopt.ssingserver.domain.auth.controller.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.sopt.ssingserver.domain.auth.dto.request.KakaoLoginRequest;
import org.sopt.ssingserver.domain.auth.dto.response.InstructorKakaoLoginResponse;
import org.sopt.ssingserver.domain.auth.error.AuthErrorCode;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.sopt.ssingserver.global.response.BaseResponse;
import org.sopt.ssingserver.global.swagger.error.ApiErrorCodes;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Instructor Auth", description = "강사 인증 API")
public interface InstructorAuthApiDocs {

    @Operation(
            summary = "강사 카카오 로그인",
            description = "카카오 Access Token을 검증하고 강사 상태를 포함한 SSING 토큰을 발급합니다."
    )
    @ApiResponse(responseCode = "200", description = "강사 카카오 로그인 성공")
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
    ResponseEntity<BaseResponse<InstructorKakaoLoginResponse>> loginWithKakao(
            @Valid @RequestBody KakaoLoginRequest request
    );
}
