package org.sopt.ssingserver.global.error;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.sopt.ssingserver.global.logging.TraceIdFilter;
import org.sopt.ssingserver.global.response.BaseResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
class ErrorResponseFactory {

    ResponseEntity<BaseResponse<Void>> error(ErrorCode errorCode, HttpServletRequest request) {
        return ResponseEntity.status(errorCode.getStatus())
                .body(BaseResponse.error(errorCode, resolveTraceId(request)));
    }

    ResponseEntity<BaseResponse<Void>> validationError(
            Map<String, String> errors,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(CommonErrorCode.VALIDATION_FAILED.getStatus())
                .body(BaseResponse.validationError(errors, resolveTraceId(request)));
    }

    String resolveTraceId(HttpServletRequest request) {
        // 실패 응답의 traceId는 TraceIdFilter가 로그 MDC에 넣은 request_id와 같은 값을 사용한다.
        Object traceId = request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE);
        return traceId != null ? traceId.toString() : null;
    }
}
