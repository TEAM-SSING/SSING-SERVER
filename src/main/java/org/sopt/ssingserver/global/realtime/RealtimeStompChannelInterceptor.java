package org.sopt.ssingserver.global.realtime;

import java.security.Principal;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.auth.token.AccessTokenClaims;
import org.sopt.ssingserver.domain.auth.token.AccessTokenProvider;
import org.sopt.ssingserver.global.security.AuthenticatedMember;
import org.sopt.ssingserver.global.security.AuthTokenExtractor;
import org.sopt.ssingserver.global.security.access.AccessAuthorizationService;
import org.sopt.ssingserver.global.security.access.CurrentMember;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RealtimeStompChannelInterceptor implements ChannelInterceptor {

    private static final Set<String> ALLOWED_SUBSCRIBE_DESTINATIONS = Set.of(
            "/user/queue/matching",
            "/user/queue/lesson"
    );

    private final AccessTokenProvider accessTokenProvider;
    private final AuthTokenExtractor authTokenExtractor;
    private final AccessAuthorizationService accessAuthorizationService;

    @Override
    public Message<?> preSend(
            Message<?> message,
            MessageChannel channel
    ) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (StompCommand.CONNECT.equals(command)) {
            authenticate(accessor);
            return message;
        }
        if (StompCommand.SUBSCRIBE.equals(command)) {
            validateSubscribe(accessor);
            return message;
        }
        if (StompCommand.SEND.equals(command)) {
            throw new AccessDeniedException("Realtime state changes must use REST APIs.");
        }

        return message;
    }

    // STOMP CONNECT 헤더의 Access Token을 검증하고 이후 개인 큐 전송 기준 Principal을 고정한다.
    private void authenticate(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);
        // 인증 성공 여부와 관계없이 이후 예외/메시지 로그에 원문 토큰이 남지 않도록 먼저 제거한다.
        accessor.removeNativeHeader(HttpHeaders.AUTHORIZATION);
        String accessToken = authTokenExtractor.extractBearerToken(authorization);
        AccessTokenClaims claims = accessTokenProvider.parseAccessToken(accessToken);
        CurrentMember currentMember = accessAuthorizationService.authorize(
                new AuthenticatedMember(claims.memberId(), claims.role())
        );
        accessor.setUser(new RealtimePrincipal(currentMember.memberId(), currentMember.role()));
    }

    // MVP에서 허용한 개인 큐만 구독 가능하게 하여 public topic/임의 destination을 fail-closed 처리한다.
    private void validateSubscribe(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        if (user == null) {
            throw new AccessDeniedException("Realtime subscription requires authentication.");
        }

        String destination = accessor.getDestination();
        if (!ALLOWED_SUBSCRIBE_DESTINATIONS.contains(destination)) {
            throw new AccessDeniedException("Realtime subscription destination is not allowed.");
        }
    }
}
