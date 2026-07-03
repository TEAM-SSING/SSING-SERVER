package org.sopt.ssingserver.domain.auth.controller;

import jakarta.validation.Valid;
import org.sopt.ssingserver.domain.auth.dto.request.KakaoLoginRequest;
import org.sopt.ssingserver.domain.auth.dto.response.AuthLoginResult;
import org.sopt.ssingserver.domain.auth.dto.response.InstructorAuthLoginResult;
import org.sopt.ssingserver.domain.auth.dto.response.InstructorKakaoLoginResponse;
import org.sopt.ssingserver.domain.auth.response.AuthSuccessCode;
import org.sopt.ssingserver.domain.auth.service.AuthService;
import org.sopt.ssingserver.global.response.BaseResponse;
import org.sopt.ssingserver.global.response.SuccessResponseFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/instructor/auth")
public class InstructorAuthController {

    private final AuthService authService;

    public InstructorAuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/kakao")
    public ResponseEntity<BaseResponse<InstructorKakaoLoginResponse>> loginWithKakao(
            @Valid @RequestBody KakaoLoginRequest request
    ) {
        InstructorAuthLoginResult instructorResult = authService.loginInstructorWithKakao(request.kakaoAccessToken());
        AuthLoginResult result = instructorResult.loginResult();
        InstructorKakaoLoginResponse response = new InstructorKakaoLoginResponse(
                result.accessToken(),
                result.refreshToken(),
                result.tokenType(),
                result.expiresIn(),
                new InstructorKakaoLoginResponse.MemberResponse(
                        result.memberId(),
                        result.nickname(),
                        result.role(),
                        result.memberStatus(),
                        instructorResult.instructorStatus()
                )
        );
        return SuccessResponseFactory.success(AuthSuccessCode.AUTH_LOGIN_SUCCESS, response);
    }
}
