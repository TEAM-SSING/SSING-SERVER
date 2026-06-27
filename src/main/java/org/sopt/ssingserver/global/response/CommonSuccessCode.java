package org.sopt.ssingserver.global.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CommonSuccessCode implements SuccessCode {

    SUCCESS(HttpStatus.OK, "요청이 성공했습니다.");

    private final HttpStatus status;
    private final String message;

    // 코드는 enum 상수 이름을 그대로 사용
    @Override
    public String getCode() {
        return name();
    }
}
