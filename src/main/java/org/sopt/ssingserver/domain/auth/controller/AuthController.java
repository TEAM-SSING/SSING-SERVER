package org.sopt.ssingserver.domain.auth.controller;

import jakarta.validation.Valid;
import org.sopt.ssingserver.domain.auth.dto.request.AuthLogoutRequest;
import org.sopt.ssingserver.domain.auth.dto.request.AuthRefreshRequest;
import org.sopt.ssingserver.domain.auth.dto.response.AuthRefreshResponse;
import org.sopt.ssingserver.domain.auth.response.AuthSuccessCode;
import org.sopt.ssingserver.domain.auth.service.AuthService;
import org.sopt.ssingserver.domain.auth.token.AccessTokenException;
import org.sopt.ssingserver.global.error.BusinessException;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.sopt.ssingserver.global.security.AuthTokenExtractor;
import org.sopt.ssingserver.global.response.BaseResponse;
import org.sopt.ssingserver.global.response.SuccessResponseFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthTokenExtractor authTokenExtractor;

    public AuthController(AuthService authService, AuthTokenExtractor authTokenExtractor) {
        this.authService = authService;
        this.authTokenExtractor = authTokenExtractor;
    }

    @PostMapping("/refresh")
    public ResponseEntity<BaseResponse<AuthRefreshResponse>> refreshAccessToken(
            @Valid @RequestBody AuthRefreshRequest request
    ) {
        AuthRefreshResponse response = authService.refreshAccessToken(request.refreshToken());
        return SuccessResponseFactory.success(AuthSuccessCode.AUTH_TOKEN_REISSUED, response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody AuthLogoutRequest request
    ) {
        String accessToken = extractAccessToken(authorization);
        authService.logout(accessToken, request.refreshToken());
        return SuccessResponseFactory.noContent(AuthSuccessCode.AUTH_LOGOUT_SUCCESS);
    }

    private String extractAccessToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            throw new BusinessException(CommonErrorCode.UNAUTHENTICATED);
        }
        try {
            return authTokenExtractor.extractBearerToken(authorization);
        } catch (AccessTokenException exception) {
            throw new BusinessException(exception.getErrorCode(), exception);
        }
    }
}
