package org.sopt.ssingserver.global.security;

import org.sopt.ssingserver.global.error.ErrorCode;
import org.springframework.security.core.AuthenticationException;

public class JwtAuthenticationFailureException extends AuthenticationException {

    private final ErrorCode errorCode;

    public JwtAuthenticationFailureException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
