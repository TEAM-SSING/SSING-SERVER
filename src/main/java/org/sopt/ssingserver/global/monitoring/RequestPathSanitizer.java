package org.sopt.ssingserver.global.monitoring;

import jakarta.servlet.http.HttpServletRequest;

/** 관측용 원본 경로를 안전한 길이와 문자로 제한한다. */
public final class RequestPathSanitizer {

    static final int MAX_RAW_PATH_LENGTH = 512;
    private static final String TRUNCATION_SUFFIX = "...";

    private RequestPathSanitizer() {
    }

    public static String rawPath(HttpServletRequest request) {
        String rawPath = request.getRequestURI();
        if (rawPath == null) {
            return null;
        }

        String sanitized = rawPath.replaceAll("[\\r\\n\\t]", "_");
        if (sanitized.length() <= MAX_RAW_PATH_LENGTH) {
            return sanitized;
        }
        return sanitized.substring(0, MAX_RAW_PATH_LENGTH - TRUNCATION_SUFFIX.length()) + TRUNCATION_SUFFIX;
    }
}
