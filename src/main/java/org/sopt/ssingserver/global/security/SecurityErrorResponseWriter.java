package org.sopt.ssingserver.global.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.global.error.ErrorCode;
import org.sopt.ssingserver.global.error.ErrorResponseFactory;
import org.sopt.ssingserver.global.monitoring.ClientErrorTrackingPolicy;
import org.sopt.ssingserver.global.response.BaseResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class SecurityErrorResponseWriter {

    private final ErrorResponseFactory errorResponseFactory;
    private final ObjectMapper objectMapper;

    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            ErrorCode errorCode
    ) throws IOException {
        if (response.isCommitted()) {
            return;
        }

        // Security 예외의 공통 실패 응답 직렬화
        ResponseEntity<BaseResponse<Void>> errorResponse = errorResponseFactory.error(errorCode, request);
        if (errorResponse.getStatusCode().is4xxClientError()) {
            ClientErrorTrackingPolicy.markDeclared(request);
        }
        response.setStatus(errorResponse.getStatusCode().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), errorResponse.getBody());
    }
}
