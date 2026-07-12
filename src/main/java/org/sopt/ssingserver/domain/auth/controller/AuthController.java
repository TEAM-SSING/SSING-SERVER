package org.sopt.ssingserver.domain.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.auth.controller.docs.AuthApiDocs;
import org.sopt.ssingserver.domain.auth.dto.request.AuthLogoutRequest;
import org.sopt.ssingserver.domain.auth.dto.request.AuthRefreshRequest;
import org.sopt.ssingserver.domain.auth.dto.response.AuthRefreshResponse;
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
@RequestMapping("/api/v1/auth")
public class AuthController implements AuthApiDocs {

    private final AuthService authService;

    @Override
    @PostMapping("/refresh")
    public ResponseEntity<BaseResponse<AuthRefreshResponse>> refreshAccessToken(
            @Valid @RequestBody AuthRefreshRequest request
    ) {
        AuthRefreshResponse response = authService.refreshAccessToken(request.refreshToken());
        return SuccessResponseFactory.success(AuthSuccessCode.AUTH_TOKEN_REISSUED, response);
    }

    @Override
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Valid @RequestBody AuthLogoutRequest request
    ) {
        authService.logout(request.refreshToken());
        return SuccessResponseFactory.noContent(AuthSuccessCode.AUTH_LOGOUT_SUCCESS);
    }
}
