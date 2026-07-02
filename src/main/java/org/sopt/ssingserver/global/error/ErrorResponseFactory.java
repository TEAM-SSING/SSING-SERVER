package org.sopt.ssingserver.global.error;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.sopt.ssingserver.global.logging.RequestIdFilter;
import org.sopt.ssingserver.global.response.BaseResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class ErrorResponseFactory {

    public ResponseEntity<BaseResponse<Void>> error(ErrorCode errorCode, HttpServletRequest request) {
        return ResponseEntity.status(errorCode.getStatus())
                .body(BaseResponse.error(errorCode, resolveRequestId(request)));
    }

    public ResponseEntity<BaseResponse<Void>> validationError(
            Map<String, String> errors,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(CommonErrorCode.VALIDATION_FAILED.getStatus())
                .body(BaseResponse.validationError(errors, resolveRequestId(request)));
    }

    public String resolveRequestId(HttpServletRequest request) {
        // 실패 응답의 requestId는 RequestIdFilter가 로그 MDC에 넣은 request_id와 같은 값을 사용한다.
        Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        return requestId != null ? requestId.toString() : null;
    }
}
