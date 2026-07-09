package org.sopt.ssingserver.global.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.sopt.ssingserver.domain.auth.error.AuthErrorCode;
import org.sopt.ssingserver.domain.auth.token.AccessTokenException;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;

class RealtimeStompErrorHandlerTest {

    private final RealtimeStompErrorHandler errorHandler = new RealtimeStompErrorHandler();

    @Test
    void 인증실패는_토큰을_노출하지_않는_ERROR_프레임으로_변환한다() {
        Message<byte[]> clientMessage = connectMessage("Bearer secret-access-token");
        MessageDeliveryException exception = new MessageDeliveryException(
                clientMessage,
                new AccessTokenException(AuthErrorCode.AUTH_INVALID_TOKEN)
        );

        Message<byte[]> result = errorHandler.handleClientMessageProcessingError(clientMessage, exception);

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(result);
        assertThat(accessor.getCommand()).isSameAs(StompCommand.ERROR);
        assertThat(accessor.getMessage()).isEqualTo("AUTH_INVALID_TOKEN");
        assertThat(result.toString()).doesNotContain("secret-access-token");
    }

    @Test
    void 권한실패는_FORBIDDEN_ERROR_프레임으로_변환한다() {
        Message<byte[]> clientMessage = connectMessage("Bearer secret-access-token");
        MessageDeliveryException exception = new MessageDeliveryException(
                clientMessage,
                new IllegalStateException(new AccessDeniedException("suspended member"))
        );

        Message<byte[]> result = errorHandler.handleClientMessageProcessingError(clientMessage, exception);

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(result);
        assertThat(accessor.getMessage()).isEqualTo(CommonErrorCode.FORBIDDEN.getCode());
        assertThat(result.getPayload()).isEmpty();
    }

    @Test
    void 알수없는_오류는_내부내용을_숨긴_INTERNAL_ERROR_프레임으로_변환한다() {
        Message<byte[]> clientMessage = connectMessage("Bearer secret-access-token");
        Logger logger = (Logger) LoggerFactory.getLogger(RealtimeStompErrorHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            Message<byte[]> result = errorHandler.handleClientMessageProcessingError(
                    clientMessage,
                    new IllegalStateException("database password leaked")
            );

            StompHeaderAccessor accessor = StompHeaderAccessor.wrap(result);
            assertThat(accessor.getMessage()).isEqualTo(CommonErrorCode.INTERNAL_ERROR.getCode());
            assertThat(result.toString()).doesNotContain("database password leaked");

            ILoggingEvent logEvent = appender.list.getFirst();
            assertThat(logEvent.getLevel()).isEqualTo(Level.ERROR);
            assertThat(logEvent.getThrowableProxy()).isNull();
            assertThat(logEvent.toString()).doesNotContain("database password leaked", "secret-access-token");
            assertThat(keyValueMap(logEvent))
                    .containsEntry("event", "realtime.stomp.client_processing.failed")
                    .containsEntry("exception_type", IllegalStateException.class.getName());
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void 브로커_ERROR_프레임도_내부본문을_제거한다() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
        accessor.setMessage("raw broker error");
        Message<byte[]> rawError = MessageBuilder.createMessage(
                "stack trace".getBytes(),
                accessor.getMessageHeaders()
        );

        Message<byte[]> result = errorHandler.handleErrorMessageToClient(rawError);

        StompHeaderAccessor safeAccessor = StompHeaderAccessor.wrap(result);
        assertThat(safeAccessor.getMessage()).isEqualTo(CommonErrorCode.INTERNAL_ERROR.getCode());
        assertThat(result.getPayload()).isEmpty();
        assertThat(result.toString()).doesNotContain("raw broker error", "stack trace");
    }

    private Message<byte[]> connectMessage(String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader(HttpHeaders.AUTHORIZATION, authorization);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Map<String, Object> keyValueMap(ILoggingEvent event) {
        return event.getKeyValuePairs().stream()
                .collect(Collectors.toMap(keyValuePair -> keyValuePair.key, keyValuePair -> keyValuePair.value));
    }
}
