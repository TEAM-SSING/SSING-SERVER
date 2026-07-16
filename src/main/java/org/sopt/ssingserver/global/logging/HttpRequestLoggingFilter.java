package org.sopt.ssingserver.global.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.sopt.ssingserver.global.monitoring.ClientErrorTrackingPolicy;
import org.sopt.ssingserver.global.monitoring.ErrorTracker;
import org.sopt.ssingserver.global.monitoring.RequestPathSanitizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class HttpRequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HttpRequestLoggingFilter.class);
    private static final String UNHANDLED_EXCEPTION_EVENT = "http.request.unhandled_exception";

    private final ErrorTracker errorTracker;

    public HttpRequestLoggingFilter() {
        this(ErrorTracker.NO_OP);
    }

    public HttpRequestLoggingFilter(ErrorTracker errorTracker) {
        this.errorTracker = errorTracker;
    }

    @Autowired
    public HttpRequestLoggingFilter(ObjectProvider<ErrorTracker> errorTracker) {
        this(errorTracker.getIfAvailable(() -> ErrorTracker.NO_OP));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long startedAt = System.currentTimeMillis();

        try {
            filterChain.doFilter(request, response);
        } catch (ServletException | RuntimeException exception) {
            // MVC 예외 처리기에 닿지 못한 필터 예외는 이 경계가 안전한 ERROR 한 번을 소유한다.
            logUnhandledFilterException(request, response, exception);
            throw exception;
        } finally {
            if (!isSuccessfulHealthCheck(request, response)) {
                // 예외가 발생해도 요청 종료 로그는 한 번 남겨 장애 추적 기준점을 유지한다.
                String path = resolveRouteTemplate(request);
                var logBuilder = log.atInfo()
                        .addKeyValue("event", "http.request.completed")
                        .addKeyValue("method", request.getMethod())
                        .addKeyValue("path", path)
                        .addKeyValue("status", response.getStatus())
                        .addKeyValue("duration_ms", System.currentTimeMillis() - startedAt);
                if ("/unmapped".equals(path)) {
                    logBuilder.addKeyValue("raw_path", RequestPathSanitizer.rawPath(request));
                }
                logBuilder.log("HTTP request completed");
            }
            if (isUnexpectedClientError(request, response)) {
                errorTracker.captureUnexpectedClientError(request, response.getStatus());
            }
        }
    }

    private void logUnhandledFilterException(
            HttpServletRequest request,
            HttpServletResponse response,
            Exception exception
    ) {
        if (!response.isCommitted() && response.getStatus() < 500) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }

        log.atError()
                .addKeyValue("event", UNHANDLED_EXCEPTION_EVENT)
                .addKeyValue("error_code", CommonErrorCode.INTERNAL_ERROR.getCode())
                .addKeyValue("status", response.getStatus())
                .addKeyValue("exception_type", exception.getClass().getName())
                .log("Unhandled servlet filter exception");
        errorTracker.capture(
                UNHANDLED_EXCEPTION_EVENT,
                CommonErrorCode.INTERNAL_ERROR,
                exception,
                request
        );
    }

    private boolean isSuccessfulHealthCheck(HttpServletRequest request, HttpServletResponse response) {
        String path = request.getRequestURI();
        boolean healthPath = path.equals("/actuator/health") || path.startsWith("/actuator/health/");
        return request.getMethod().equals("GET")
                && healthPath
                && response.getStatus() >= 200
                && response.getStatus() < 300;
    }

    private boolean isUnexpectedClientError(HttpServletRequest request, HttpServletResponse response) {
        return response.getStatus() >= 400
                && response.getStatus() < 500
                && !ClientErrorTrackingPolicy.isDeclared(request);
    }

    private String resolveRouteTemplate(HttpServletRequest request) {
        // 실제 ID가 포함된 URI 대신 라우트 템플릿만 기록해 개인정보와 고카디널리티 유입을 막는다.
        Object routeTemplate = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return routeTemplate == null ? "/unmapped" : routeTemplate.toString();
    }
}
