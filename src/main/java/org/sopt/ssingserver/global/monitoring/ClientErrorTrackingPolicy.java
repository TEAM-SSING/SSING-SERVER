package org.sopt.ssingserver.global.monitoring;

import jakarta.servlet.http.HttpServletRequest;

/** 명시적으로 응답한 4xx와 추적이 필요한 예상 밖 4xx를 구분한다. */
public final class ClientErrorTrackingPolicy {

    private static final String DECLARED_CLIENT_ERROR_ATTRIBUTE =
            ClientErrorTrackingPolicy.class.getName() + ".declaredClientError";

    private ClientErrorTrackingPolicy() {
    }

    public static void markDeclared(HttpServletRequest request) {
        request.setAttribute(DECLARED_CLIENT_ERROR_ATTRIBUTE, Boolean.TRUE);
    }

    public static boolean isDeclared(HttpServletRequest request) {
        return Boolean.TRUE.equals(request.getAttribute(DECLARED_CLIENT_ERROR_ATTRIBUTE));
    }
}
