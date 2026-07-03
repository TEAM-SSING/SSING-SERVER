package org.sopt.ssingserver.global.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpMethod;

final class SecurityPublicPaths {

    static final String ERROR_PATH = "/error";

    private static final List<String> PUBLIC_POST_AUTH_PATHS = List.of(
            "/api/v1/consumer/auth/kakao",
            "/api/v1/instructor/auth/kakao",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout"
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
        return HttpMethod.POST.matches(request.getMethod())
                && PUBLIC_POST_AUTH_PATHS.contains(request.getServletPath());
    }
}
