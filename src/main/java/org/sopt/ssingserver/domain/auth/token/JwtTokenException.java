package org.sopt.ssingserver.domain.auth.token;

import org.sopt.ssingserver.domain.auth.error.AuthErrorCode;

public class JwtTokenException extends RuntimeException {

    private final AuthErrorCode errorCode;

    public JwtTokenException(AuthErrorCode errorCode) {
        this.errorCode = errorCode;
    }

    public JwtTokenException(AuthErrorCode errorCode, Throwable cause) {
        super(cause);
        this.errorCode = errorCode;
    }

    public AuthErrorCode getErrorCode() {
        return errorCode;
    }
}
