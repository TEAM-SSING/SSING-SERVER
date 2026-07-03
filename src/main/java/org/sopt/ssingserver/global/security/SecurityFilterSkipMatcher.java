package org.sopt.ssingserver.global.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

@Component
public class SecurityFilterSkipMatcher {

    private static final Set<String> PUBLIC_POST_AUTH_PATHS = Set.of(
            "/api/v1/consumer/auth/kakao",
            "/api/v1/instructor/auth/kakao",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout"
    );

    public boolean shouldSkip(HttpServletRequest request) {
        if ("/error".equals(request.getServletPath())) {
            // Spring 오류 응답 재진입 방지
            return true;
        }
        // 인증 API별 자체 토큰 처리 허용
        return HttpMethod.POST.matches(request.getMethod())
                && PUBLIC_POST_AUTH_PATHS.contains(request.getServletPath());
    }
}
