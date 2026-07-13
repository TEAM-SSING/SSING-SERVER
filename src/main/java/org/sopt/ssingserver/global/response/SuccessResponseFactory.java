package org.sopt.ssingserver.global.response;

import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

// 성공 응답 생성 경로를 한 곳으로 모아 HTTP status와 body 정책을 일관되게 강제한다.
public final class SuccessResponseFactory {

    private SuccessResponseFactory() {
    }

    public static <T> ResponseEntity<BaseResponse<T>> success(SuccessCode successCode, T data) {
        SuccessCode requiredSuccessCode = Objects.requireNonNull(successCode, "successCode");
        return ResponseEntity.status(requiredSuccessCode.getStatus())
                .body(BaseResponse.success(requiredSuccessCode, data));
    }

    public static <T> ResponseEntity<T> noContent(SuccessCode successCode) {
        SuccessCode requiredSuccessCode = Objects.requireNonNull(successCode, "successCode");
        // 204 응답은 body가 없어야 하므로 별도 메서드에서 상태값까지 함께 검증한다.
        if (requiredSuccessCode.getStatus() != HttpStatus.NO_CONTENT) {
            throw new IllegalArgumentException("No content responses must use HTTP 204 status.");
        }

        return ResponseEntity.noContent().build();
    }
}
