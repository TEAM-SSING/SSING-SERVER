package org.sopt.ssingserver.domain.auth.dev.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum DevAuthErrorCode implements ErrorCode {

    DEV_PERSONA_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 개발용 persona를 찾을 수 없습니다."),
    DEV_PERSONA_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 존재하는 개발용 personaKey입니다."),
    DEV_PERSONA_INVALID_TEMPLATE(HttpStatus.BAD_REQUEST, "지원하지 않는 개발용 persona template입니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
