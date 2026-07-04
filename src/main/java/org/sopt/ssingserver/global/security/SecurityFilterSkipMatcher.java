package org.sopt.ssingserver.global.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class SecurityFilterSkipMatcher {

    public boolean shouldSkip(HttpServletRequest request) {
        if (SecurityPublicPaths.isErrorPath(request)) {
            // Spring 오류 응답 재진입 방지
            return true;
        }
        // 인증 API별 자체 토큰 처리 허용
        return SecurityPublicPaths.isPublicPostAuthPath(request);
    }
}
