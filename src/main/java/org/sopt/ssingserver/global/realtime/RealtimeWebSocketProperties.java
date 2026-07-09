package org.sopt.ssingserver.global.realtime;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ssing.realtime.websocket")
public record RealtimeWebSocketProperties(
        List<String> allowedOrigins
) {

    public RealtimeWebSocketProperties {
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            allowedOrigins = List.of("*");
        }
    }

    String[] allowedOriginPatterns() {
        return allowedOrigins.toArray(String[]::new);
    }
}
