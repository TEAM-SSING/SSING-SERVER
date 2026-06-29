package org.sopt.ssingserver.domain.auth.token;

public enum TokenType {
    // Refresh Token은 opaque random token 정책이므로 JWT tokenType에는 Access만 둔다.
    ACCESS
}
