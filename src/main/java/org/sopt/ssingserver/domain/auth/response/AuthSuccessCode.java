package org.sopt.ssingserver.domain.auth.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.global.response.SuccessCode;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthSuccessCode implements SuccessCode {

    AUTH_LOGIN_SUCCESS(HttpStatus.OK, "로그인에 성공했습니다."),
    AUTH_TOKEN_REISSUED(HttpStatus.OK, "Access Token이 재발급되었습니다."),
    AUTH_LOGOUT_SUCCESS(HttpStatus.NO_CONTENT, "로그아웃에 성공했습니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
