package org.sopt.ssingserver.global.monitoring;

import jakarta.servlet.http.HttpServletRequest;
import org.sopt.ssingserver.global.error.ErrorCode;

public interface ErrorTracker {

    ErrorTracker NO_OP = (eventName, errorCode, exception, request) -> {
    };

    void capture(String eventName, ErrorCode errorCode, Throwable exception, HttpServletRequest request);
}
