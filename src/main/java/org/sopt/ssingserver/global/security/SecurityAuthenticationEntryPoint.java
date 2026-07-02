package org.sopt.ssingserver.global.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.sopt.ssingserver.global.error.ErrorCode;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecurityAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final SecurityErrorResponseWriter errorResponseWriter;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        ErrorCode errorCode = resolveErrorCode(authException);
        errorResponseWriter.write(request, response, errorCode);
    }

    private ErrorCode resolveErrorCode(AuthenticationException authException) {
        if (authException instanceof AccessTokenAuthenticationException accessTokenException) {
            return accessTokenException.getErrorCode();
        }
        return CommonErrorCode.UNAUTHENTICATED;
    }
}
