package org.sopt.ssingserver.domain.auth.token;

import org.sopt.ssingserver.domain.auth.error.AuthErrorCode;

public class AccessTokenException extends RuntimeException {

    private final AuthErrorCode errorCode;

    public AccessTokenException(AuthErrorCode errorCode) {
        this.errorCode = errorCode;
    }

    public AccessTokenException(AuthErrorCode errorCode, Throwable cause) {
        super(cause);
        this.errorCode = errorCode;
    }

    public AuthErrorCode getErrorCode() {
        return errorCode;
    }
}
