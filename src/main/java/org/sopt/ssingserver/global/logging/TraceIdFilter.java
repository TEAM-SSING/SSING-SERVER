package org.sopt.ssingserver.global.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청마다 traceId를 부여하는 서블릿 필터.
 *
 * <p>{@code X-Request-Id} 헤더를 재사용하거나 새로 생성한 traceId를 MDC({@code request_id}) · 요청 attribute · 응답 헤더에
 * 실어 로그 · 에러 응답 · 클라이언트가 같은 값으로 하나의 요청 흐름을 추적하게 한다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Request-Id";
    public static final String TRACE_ID_ATTRIBUTE = "traceId";
    public static final String REQUEST_ID_MDC_KEY = "request_id";
    private static final int MAX_TRACE_ID_LENGTH = 64;
    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("[A-Za-z0-9._-]+");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String traceId = resolveTraceId(request);

        request.setAttribute(TRACE_ID_ATTRIBUTE, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        MDC.put(REQUEST_ID_MDC_KEY, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // 서블릿 스레드는 재사용되므로 요청이 끝나면 이전 request_id가 섞이지 않게 제거한다.
            MDC.remove(REQUEST_ID_MDC_KEY);
        }
    }

    // 신뢰 가능한 형식의 X-Request-Id만 재사용하고, 그 외 값은 새로 생성한다.
    private String resolveTraceId(HttpServletRequest request) {
        String headerTraceId = request.getHeader(TRACE_ID_HEADER);
        if (isValidTraceId(headerTraceId)) {
            return headerTraceId;
        }
        return UUID.randomUUID().toString();
    }

    // 클라이언트가 보낸 값을 로그와 응답에 쓰기 전, 길이와 문자 범위를 제한해 로그 오염을 막는다.
    private boolean isValidTraceId(String traceId) {
        return traceId != null
                && !traceId.isBlank()
                && traceId.length() <= MAX_TRACE_ID_LENGTH
                && TRACE_ID_PATTERN.matcher(traceId).matches();
    }
}
