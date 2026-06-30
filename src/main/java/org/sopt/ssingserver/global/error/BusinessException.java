package org.sopt.ssingserver.global.error;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, Throwable cause) {
        // 원인 예외는 응답에 노출하지 않지만, 로그와 디버깅에서 추적할 수 있도록 보존한다.
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }
}
