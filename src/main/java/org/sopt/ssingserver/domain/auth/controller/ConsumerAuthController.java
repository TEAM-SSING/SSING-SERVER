package org.sopt.ssingserver.domain.auth.controller;

import jakarta.validation.Valid;
import org.sopt.ssingserver.domain.auth.dto.request.KakaoLoginRequest;
import org.sopt.ssingserver.domain.auth.dto.response.AuthLoginResult;
import org.sopt.ssingserver.domain.auth.dto.response.ConsumerKakaoLoginResponse;
import org.sopt.ssingserver.domain.auth.repository.OAuthAccountRepository;
import org.sopt.ssingserver.domain.auth.response.AuthSuccessCode;
import org.sopt.ssingserver.domain.auth.service.AuthService;
import org.sopt.ssingserver.global.response.BaseResponse;
import org.sopt.ssingserver.global.response.SuccessResponseFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnBean(OAuthAccountRepository.class)
@RequestMapping("/api/v1/consumer/auth")
public class ConsumerAuthController {

    private final AuthService authService;

    public ConsumerAuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/kakao")
    public ResponseEntity<BaseResponse<ConsumerKakaoLoginResponse>> loginWithKakao(
            @Valid @RequestBody KakaoLoginRequest request
    ) {
        AuthLoginResult result = authService.loginConsumerWithKakao(request.kakaoAccessToken());
        ConsumerKakaoLoginResponse response = new ConsumerKakaoLoginResponse(
                result.accessToken(),
                result.refreshToken(),
                result.tokenType(),
                result.expiresIn(),
                new ConsumerKakaoLoginResponse.MemberResponse(
                        result.memberId(),
                        result.nickname(),
                        result.role(),
                        result.memberStatus()
                )
        );
        return SuccessResponseFactory.success(AuthSuccessCode.AUTH_LOGIN_SUCCESS, response);
    }
}
