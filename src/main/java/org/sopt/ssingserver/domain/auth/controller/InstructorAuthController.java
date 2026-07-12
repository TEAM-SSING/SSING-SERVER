package org.sopt.ssingserver.domain.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.auth.controller.docs.InstructorAuthApiDocs;
import org.sopt.ssingserver.domain.auth.dto.request.KakaoLoginRequest;
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
@RequiredArgsConstructor
@RequestMapping("/api/v1/instructor/auth")
public class InstructorAuthController implements InstructorAuthApiDocs {

    private final AuthService authService;

    @Override
    @PostMapping("/kakao")
    public ResponseEntity<BaseResponse<InstructorKakaoLoginResponse>> loginWithKakao(
            @Valid @RequestBody KakaoLoginRequest request
    ) {
        InstructorAuthLoginResult instructorResult = authService.loginInstructorWithKakao(request.kakaoAccessToken());
        InstructorKakaoLoginResponse response = InstructorKakaoLoginResponse.from(instructorResult);
        return SuccessResponseFactory.success(AuthSuccessCode.AUTH_LOGIN_SUCCESS, response);
    }
}
