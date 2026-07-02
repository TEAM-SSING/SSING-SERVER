package org.sopt.ssingserver.global.security;

import org.sopt.ssingserver.global.error.ErrorCode;
import org.springframework.security.core.AuthenticationException;

public class AccessTokenAuthenticationException extends AuthenticationException {

    private final ErrorCode errorCode;

    public AccessTokenAuthenticationException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
