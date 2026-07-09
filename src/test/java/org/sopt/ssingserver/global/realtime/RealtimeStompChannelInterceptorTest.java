package org.sopt.ssingserver.global.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.auth.token.AccessTokenClaims;
import org.sopt.ssingserver.domain.auth.token.AccessTokenException;
import org.sopt.ssingserver.domain.auth.token.AccessTokenProvider;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;
import org.sopt.ssingserver.global.security.AuthenticatedMember;
import org.sopt.ssingserver.global.security.AuthTokenExtractor;
import org.sopt.ssingserver.global.security.access.AccessAuthorizationService;
import org.sopt.ssingserver.global.security.access.CurrentMember;
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
    private final AccessAuthorizationService accessAuthorizationService = mock(AccessAuthorizationService.class);
    private final RealtimeStompChannelInterceptor interceptor = new RealtimeStompChannelInterceptor(
            accessTokenProvider,
            new AuthTokenExtractor(),
            accessAuthorizationService
    );

    @Test
    void CONNECT는_Authorization_토큰을_검증하고_memberId를_Principal_name으로_등록한다() {
        AccessTokenClaims claims = new AccessTokenClaims(12L, MemberRole.CONSUMER, null, null);
        AuthenticatedMember authenticatedMember = new AuthenticatedMember(12L, MemberRole.CONSUMER);
        when(accessTokenProvider.parseAccessToken("access-token")).thenReturn(claims);
        when(accessAuthorizationService.authorize(authenticatedMember))
                .thenReturn(new CurrentMember(12L, MemberRole.CONSUMER, MemberStatus.ACTIVE, null));
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
        assertThat(accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION)).isNull();
        verify(accessAuthorizationService).authorize(authenticatedMember);
    }

    @Test
    void CONNECT는_토큰이_없으면_거부한다() {
        Message<byte[]> message = stompMessage(StompCommand.CONNECT, null, null, null);

        assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
                .isInstanceOf(AccessTokenException.class);
    }

    @Test
    void CONNECT는_토큰검증에_실패해도_Authorization_헤더를_메시지에_남기지_않는다() {
        when(accessTokenProvider.parseAccessToken("invalid-token"))
                .thenThrow(new AccessTokenException(org.sopt.ssingserver.domain.auth.error.AuthErrorCode.AUTH_INVALID_TOKEN));
        Message<byte[]> message = stompMessage(
                StompCommand.CONNECT,
                null,
                null,
                "Bearer invalid-token"
        );

        assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
                .isInstanceOf(AccessTokenException.class);

        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        assertThat(accessor).isNotNull();
        assertThat(accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION)).isNull();
        assertThat(message.toString()).doesNotContain("invalid-token");
    }

    @Test
    void CONNECT는_DB의_현재_회원이_허용되지_않으면_Principal을_등록하지_않는다() {
        AccessTokenClaims claims = new AccessTokenClaims(12L, MemberRole.CONSUMER, null, null);
        AuthenticatedMember authenticatedMember = new AuthenticatedMember(12L, MemberRole.CONSUMER);
        when(accessTokenProvider.parseAccessToken("access-token")).thenReturn(claims);
        when(accessAuthorizationService.authorize(authenticatedMember))
                .thenThrow(new AccessDeniedException("suspended member"));
        Message<byte[]> message = stompMessage(
                StompCommand.CONNECT,
                null,
                null,
                "Bearer access-token"
        );

        assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
                .isInstanceOf(AccessDeniedException.class);

        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        assertThat(accessor).isNotNull();
        assertThat(accessor.getUser()).isNull();
        assertThat(accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION)).isNull();
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
