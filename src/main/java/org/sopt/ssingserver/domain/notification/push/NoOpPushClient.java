package org.sopt.ssingserver.domain.notification.push;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!dev")
public class NoOpPushClient implements PushClient {

    @Override
    public void send(PushMessage message) {
        log.info("Push delivery skipped because Firebase is disabled for this profile.");
    }
}
