package org.sopt.ssingserver.global.monitoring;

import io.sentry.SentryEvent;
import io.sentry.SentryOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("dev")
public class SentryMonitoringConfig {

    @Bean
    SentryOptions.BeforeSendCallback ssingSentryBeforeSend() {
        return (event, hint) -> sanitize(event);
    }

    static SentryEvent sanitize(SentryEvent event) {
        if (!SentryErrorTracker.MANAGED_BY.equals(event.getTag(SentryErrorTracker.MANAGED_BY_TAG))) {
            return null;
        }

        event.setRequest(null);
        event.setUser(null);
        event.setExtras(null);
        return event;
    }
}
