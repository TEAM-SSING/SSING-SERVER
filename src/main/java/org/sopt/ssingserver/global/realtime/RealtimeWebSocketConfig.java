package org.sopt.ssingserver.global.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@EnableConfigurationProperties(RealtimeWebSocketProperties.class)
@RequiredArgsConstructor
public class RealtimeWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final long HEARTBEAT_INTERVAL_MILLIS = 10_000L;

    private final RealtimeStompChannelInterceptor realtimeStompChannelInterceptor;
    private final RealtimeWebSocketProperties realtimeWebSocketProperties;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Android STOMP 클라이언트가 처음 연결하는 WebSocket handshake endpoint
        registry.addEndpoint("/ws/realtime")
                .setAllowedOriginPatterns(realtimeWebSocketProperties.allowedOriginPatterns());
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 서버는 /queue로 보내고, 클라이언트는 /user/queue/... 개인 큐를 구독한다.
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
        registry.enableSimpleBroker("/queue")
                .setTaskScheduler(realtimeMessageBrokerTaskScheduler())
                .setHeartbeatValue(new long[]{HEARTBEAT_INTERVAL_MILLIS, HEARTBEAT_INTERVAL_MILLIS});
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(realtimeStompChannelInterceptor);
    }

    @Bean
    TaskScheduler realtimeMessageBrokerTaskScheduler() {
        // simple broker heartbeat 전송에 사용할 전용 스케줄러
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("realtime-ws-heartbeat-");
        return scheduler;
    }
}
