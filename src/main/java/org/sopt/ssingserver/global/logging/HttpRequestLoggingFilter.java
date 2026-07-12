package org.sopt.ssingserver.global.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class HttpRequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HttpRequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long startedAt = System.currentTimeMillis();

        try {
            filterChain.doFilter(request, response);
        } finally {
            if (!isSuccessfulHealthCheck(request, response)) {
                // 예외가 발생해도 요청 종료 로그는 한 번 남겨 장애 추적 기준점을 유지한다.
                log.atInfo()
                        .addKeyValue("event", "http.request.completed")
                        .addKeyValue("method", request.getMethod())
                        .addKeyValue("path", resolveRouteTemplate(request))
                        .addKeyValue("status", response.getStatus())
                        .addKeyValue("duration_ms", System.currentTimeMillis() - startedAt)
                        .log("HTTP request completed");
            }
        }
    }

    private boolean isSuccessfulHealthCheck(HttpServletRequest request, HttpServletResponse response) {
        String path = request.getRequestURI();
        boolean healthPath = path.equals("/actuator/health") || path.startsWith("/actuator/health/");
        return request.getMethod().equals("GET")
                && healthPath
                && response.getStatus() >= 200
                && response.getStatus() < 300;
    }

    private String resolveRouteTemplate(HttpServletRequest request) {
        // 실제 ID가 포함된 URI 대신 라우트 템플릿만 기록해 개인정보와 고카디널리티 유입을 막는다.
        Object routeTemplate = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return routeTemplate == null ? "/unmapped" : routeTemplate.toString();
    }
}
