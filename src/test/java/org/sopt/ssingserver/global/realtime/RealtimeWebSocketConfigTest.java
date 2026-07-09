package org.sopt.ssingserver.global.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;

class RealtimeWebSocketConfigTest {

    private final RealtimeStompChannelInterceptor interceptor = mock(RealtimeStompChannelInterceptor.class);
    private final RealtimeWebSocketConfig config = new RealtimeWebSocketConfig(
            interceptor,
            new RealtimeWebSocketProperties(List.of("https://ssing.example"))
    );

    @Test
    void registerStompEndpoints는_ws_realtime_endpoint와_allowed_origin을_등록한다() {
        StompEndpointRegistry registry = mock(StompEndpointRegistry.class);
        StompWebSocketEndpointRegistration registration = mock(StompWebSocketEndpointRegistration.class);
        when(registry.addEndpoint("/ws/realtime")).thenReturn(registration);
        when(registration.setAllowedOriginPatterns("https://ssing.example")).thenReturn(registration);

        config.registerStompEndpoints(registry);

        verify(registry).addEndpoint("/ws/realtime");
        verify(registration).setAllowedOriginPatterns("https://ssing.example");
    }

    @Test
    void configureMessageBroker는_개인큐와_heartbeat_scheduler를_설정한다() {
        TestMessageBrokerRegistry registry = new TestMessageBrokerRegistry(
                mock(SubscribableChannel.class),
                mock(MessageChannel.class)
        );

        config.configureMessageBroker(registry);

        assertThat(registry.applicationDestinationPrefixes()).containsExactly("/app");
        assertThat(registry.userDestinationPrefix()).isEqualTo("/user");
        SimpleBrokerMessageHandler simpleBroker = registry.simpleBroker(mock(SubscribableChannel.class));
        assertThat(simpleBroker.getDestinationPrefixes()).containsExactly("/queue");
        assertThat(simpleBroker.getHeartbeatValue()).containsExactly(10_000L, 10_000L);
        assertThat(simpleBroker.getTaskScheduler()).isNotNull();
    }

    @Test
    void configureClientInboundChannel은_STOMP_인증_인터셉터를_등록한다() {
        TestChannelRegistration registration = new TestChannelRegistration();

        config.configureClientInboundChannel(registration);

        assertThat(registration.interceptors()).containsExactly(interceptor);
    }

    @Test
    void realtimeMessageBrokerTaskScheduler는_heartbeat용_scheduler를_생성한다() {
        TaskScheduler taskScheduler = config.realtimeMessageBrokerTaskScheduler();

        assertThat(taskScheduler).isNotNull();
    }

    private static class TestMessageBrokerRegistry extends MessageBrokerRegistry {

        TestMessageBrokerRegistry(
                SubscribableChannel clientInboundChannel,
                MessageChannel clientOutboundChannel
        ) {
            super(clientInboundChannel, clientOutboundChannel);
        }

        Collection<String> applicationDestinationPrefixes() {
            return getApplicationDestinationPrefixes();
        }

        String userDestinationPrefix() {
            return getUserDestinationPrefix();
        }

        SimpleBrokerMessageHandler simpleBroker(SubscribableChannel brokerChannel) {
            return getSimpleBroker(brokerChannel);
        }
    }

    private static class TestChannelRegistration extends ChannelRegistration {

        List<ChannelInterceptor> interceptors() {
            return getInterceptors();
        }
    }
}
