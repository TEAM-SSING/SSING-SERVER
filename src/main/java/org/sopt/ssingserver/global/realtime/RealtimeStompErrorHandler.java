package org.sopt.ssingserver.global.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sopt.ssingserver.domain.auth.token.AccessTokenException;
import org.sopt.ssingserver.global.error.BusinessException;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.sopt.ssingserver.global.error.ErrorCode;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

@Component
public class RealtimeStompErrorHandler extends StompSubProtocolErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(RealtimeStompErrorHandler.class);

    // 프레임 원문이나 예외 메시지 대신 SSING의 안전한 에러 코드만 ERROR frame에 노출한다.
    @Override
    public Message<byte[]> handleClientMessageProcessingError(
            Message<byte[]> clientMessage,
        Throwable exception
    ) {
        ErrorCode errorCode = resolveErrorCode(exception);
        if (errorCode == CommonErrorCode.INTERNAL_ERROR) {
            logInternalError(exception);
        }
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
        accessor.setMessage(errorCode.getCode());
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    // 브로커에서 생성한 ERROR frame도 내부 예외 본문을 그대로 전달하지 않는다.
    @Override
    public Message<byte[]> handleErrorMessageToClient(Message<byte[]> errorMessage) {
        log.atError()
                .addKeyValue("event", "realtime.stomp.broker_error.sanitized")
                .log("Realtime broker error was sanitized");
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
        accessor.setMessage(CommonErrorCode.INTERNAL_ERROR.getCode());
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private ErrorCode resolveErrorCode(Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof AccessTokenException accessTokenException) {
                return accessTokenException.getErrorCode();
            }
            if (cause instanceof BusinessException businessException) {
                return businessException.getErrorCode();
            }
            if (cause instanceof AccessDeniedException) {
                return CommonErrorCode.FORBIDDEN;
            }
            cause = cause.getCause();
        }
        return CommonErrorCode.INTERNAL_ERROR;
    }

    private void logInternalError(Throwable exception) {
        Throwable rootCause = exception;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        log.atError()
                .addKeyValue("event", "realtime.stomp.client_processing.failed")
                .addKeyValue("exception_type", exception.getClass().getName())
                .addKeyValue("root_cause_type", rootCause.getClass().getName())
                .log("Realtime STOMP client message processing failed");
    }
}
