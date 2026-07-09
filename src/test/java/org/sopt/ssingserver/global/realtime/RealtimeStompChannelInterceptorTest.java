package org.sopt.ssingserver.global.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.Principal;
import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.auth.token.AccessTokenClaims;
import org.sopt.ssingserver.domain.auth.token.AccessTokenException;
import org.sopt.ssingserver.domain.auth.token.AccessTokenProvider;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.global.security.AuthTokenExtractor;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;

class RealtimeStompChannelInterceptorTest {

    private final AccessTokenProvider accessTokenProvider = mock(AccessTokenProvider.class);
    private final RealtimeStompChannelInterceptor interceptor = new RealtimeStompChannelInterceptor(
            accessTokenProvider,
            new AuthTokenExtractor()
    );

    @Test
    void CONNECT는_Authorization_토큰을_검증하고_memberId를_Principal_name으로_등록한다() {
        when(accessTokenProvider.parseAccessToken("access-token"))
                .thenReturn(new AccessTokenClaims(12L, MemberRole.CONSUMER, null, null));
        Message<byte[]> message = stompMessage(
                StompCommand.CONNECT,
                null,
                null,
                "Bearer access-token"
        );

        Message<?> result = interceptor.preSend(message, mock(MessageChannel.class));

        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class);
        assertThat(accessor).isNotNull();
        assertThat(accessor.getUser())
                .isInstanceOf(RealtimePrincipal.class)
                .extracting(Principal::getName)
                .isEqualTo("12");
    }

    @Test
    void CONNECT는_토큰이_없으면_거부한다() {
        Message<byte[]> message = stompMessage(StompCommand.CONNECT, null, null, null);

        assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
                .isInstanceOf(AccessTokenException.class);
    }

    @Test
    void SUBSCRIBE는_인증사용자의_매칭_개인큐만_허용한다() {
        Message<byte[]> message = stompMessage(
                StompCommand.SUBSCRIBE,
                "/user/queue/matching",
                new RealtimePrincipal(12L, MemberRole.CONSUMER),
                null
        );

        Message<?> result = interceptor.preSend(message, mock(MessageChannel.class));

        assertThat(result).isSameAs(message);
    }

    @Test
    void SUBSCRIBE는_허용되지_않은_destination을_거부한다() {
        Message<byte[]> message = stompMessage(
                StompCommand.SUBSCRIBE,
                "/topic/matching",
                new RealtimePrincipal(12L, MemberRole.CONSUMER),
                null
        );

        assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void SUBSCRIBE는_인증_Principal이_없으면_거부한다() {
        Message<byte[]> message = stompMessage(
                StompCommand.SUBSCRIBE,
                "/user/queue/matching",
                null,
                null
        );

        assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void SEND는_상태변경을_REST로만_처리하기_위해_거부한다() {
        Message<byte[]> message = stompMessage(
                StompCommand.SEND,
                "/app/matching/accept",
                new RealtimePrincipal(12L, MemberRole.CONSUMER),
                null
        );

        assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
                .isInstanceOf(AccessDeniedException.class);
    }

    private Message<byte[]> stompMessage(
            StompCommand command,
            String destination,
            Principal user,
            String authorization
    ) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setLeaveMutable(true);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        if (user != null) {
            accessor.setUser(user);
        }
        if (authorization != null) {
            accessor.setNativeHeader(HttpHeaders.AUTHORIZATION, authorization);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
