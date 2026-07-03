package org.sopt.ssingserver.domain.auth.dev.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.global.response.SuccessCode;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum DevAuthSuccessCode implements SuccessCode {

    DEV_PERSONA_CREATED(HttpStatus.CREATED, "개발용 persona가 생성되었습니다."),
    DEV_AUTH_TOKEN_ISSUED(HttpStatus.OK, "개발용 토큰이 발급되었습니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
