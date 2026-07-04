package org.sopt.ssingserver.global.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

final class SecurityPublicPaths {

    static final String ERROR_PATH = "/error";

    private static final List<String> PUBLIC_POST_AUTH_PATHS = List.of(
            "/api/v1/consumer/auth/kakao",
            "/api/v1/instructor/auth/kakao",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout"
    );
    private static final PathPatternRequestMatcher.Builder PATH_MATCHER = PathPatternRequestMatcher.withDefaults();
    private static final RequestMatcher PUBLIC_POST_AUTH_MATCHER = new OrRequestMatcher(
            PUBLIC_POST_AUTH_PATHS.stream()
                    .map(path -> PATH_MATCHER.matcher(HttpMethod.POST, path))
                    .toList()
    );

    private SecurityPublicPaths() {
    }

    static String[] publicPostAuthPaths() {
        return PUBLIC_POST_AUTH_PATHS.toArray(String[]::new);
    }

    static boolean isErrorPath(HttpServletRequest request) {
        return ERROR_PATH.equals(request.getServletPath());
    }

    static boolean isPublicPostAuthPath(HttpServletRequest request) {
        return PUBLIC_POST_AUTH_MATCHER.matches(request);
    }
}
