package org.sopt.ssingserver.global.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.sopt.ssingserver.global.logging.RequestIdFilter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

class SentryErrorTrackerTest {

    @Test
    void 요청의_안전한_추적_필드만_이벤트_데이터로_변환한다() {
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/api/v1/lessons/12");
        request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "req-sentry-1");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/lessons/{lessonId}");

        SentryErrorTracker.EventData event = SentryErrorTracker.EventData.from(
                "http.request.unhandled_exception",
                CommonErrorCode.INTERNAL_ERROR,
                new IllegalStateException("secret-detail"),
                request
        );

        assertThat(event.eventName()).isEqualTo("http.request.unhandled_exception");
        assertThat(event.requestId()).isEqualTo("req-sentry-1");
        assertThat(event.errorCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(event.status()).isEqualTo(500);
        assertThat(event.exceptionType()).isEqualTo(IllegalStateException.class.getName());
        assertThat(event.method()).isEqualTo("PATCH");
        assertThat(event.route()).isEqualTo("/api/v1/lessons/{lessonId}");
    }

    @Test
    void 라우트_템플릿이_없으면_raw_URI_대신_unmapped를_사용한다() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/unknown/private-value");

        SentryErrorTracker.EventData event = SentryErrorTracker.EventData.from(
                "http.request.unhandled_exception",
                CommonErrorCode.INTERNAL_ERROR,
                new IllegalArgumentException("secret-detail"),
                request
        );

        assertThat(event.route()).isEqualTo("/unmapped");
        assertThat(event.route()).doesNotContain("private-value");
    }
}
