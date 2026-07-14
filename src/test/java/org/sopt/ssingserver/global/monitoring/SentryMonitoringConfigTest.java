package org.sopt.ssingserver.global.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import io.sentry.SentryEvent;
import io.sentry.protocol.Message;
import io.sentry.protocol.Request;
import io.sentry.protocol.User;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SentryMonitoringConfigTest {

    @Test
    void ssing_error_tracker가_보낸_이벤트만_통과시킨다() {
        SentryEvent automaticEvent = new SentryEvent(new IllegalStateException("secret-detail"));

        assertThat(SentryMonitoringConfig.sanitize(automaticEvent)).isNull();
    }

    @Test
    void 요청_user_extra는_제거하고_exception_message와_stack_trace는_유지한다() {
        IllegalStateException exception = new IllegalStateException("diagnostic-message");
        SentryEvent event = new SentryEvent(exception);
        event.setTag(SentryErrorTracker.MANAGED_BY_TAG, SentryErrorTracker.MANAGED_BY);
        event.setRequest(requestWithSensitiveData());
        event.setUser(userWithSensitiveData());
        event.setExtra("access_token", "secret-token");
        Message message = new Message();
        message.setFormatted("Unhandled server exception");
        event.setMessage(message);

        SentryEvent sanitized = SentryMonitoringConfig.sanitize(event);

        assertThat(sanitized).isSameAs(event);
        assertThat(sanitized.getRequest()).isNull();
        assertThat(sanitized.getUser()).isNull();
        assertThat(sanitized.getExtras()).isNull();
        assertThat(sanitized.getThrowable()).isSameAs(exception);
        assertThat(sanitized.getThrowable()).hasMessage("diagnostic-message");
        assertThat(sanitized.getMessage().getFormatted()).isEqualTo("Unhandled server exception");
    }

    private Request requestWithSensitiveData() {
        Request request = new Request();
        request.setUrl("https://dev-api.ssing.app/private?token=secret-token");
        request.setQueryString("token=secret-token");
        request.setCookies("SESSION=secret-cookie");
        request.setHeaders(Map.of("Authorization", "Bearer secret-token"));
        request.setData(Map.of("password", "secret-password"));
        return request;
    }

    private User userWithSensitiveData() {
        User user = new User();
        user.setEmail("member@example.com");
        user.setIpAddress("127.0.0.1");
        return user;
    }
}
