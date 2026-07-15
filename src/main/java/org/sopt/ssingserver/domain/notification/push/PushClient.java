package org.sopt.ssingserver.domain.notification.push;

public interface PushClient {

    void send(PushMessage message);
}
