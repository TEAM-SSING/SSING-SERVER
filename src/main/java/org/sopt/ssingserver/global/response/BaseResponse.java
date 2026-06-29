package org.sopt.ssingserver.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;
import java.util.Objects;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.sopt.ssingserver.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BaseResponse<T>(
        boolean success,
        String code,
        String message,
        T data,
        Map<String, String> errors,
        String traceId
) {

    // 컨트롤러가 직접 호출하지 못하게 두어 SuccessResponseFactory의 204 body 검증을 우회하지 않게 한다.
    static <T> BaseResponse<T> success(SuccessCode successCode, T data) {
        validateSuccessBodyAllowed(successCode);
        return new BaseResponse<>(true, successCode.getCode(), successCode.getMessage(), data, null, null);
    }

    // 실패 응답
    public static BaseResponse<Void> error(ErrorCode errorCode, String traceId) {
        return new BaseResponse<>(false, errorCode.getCode(), errorCode.getMessage(), null, null, traceId);
    }

    // validation은 field별 code를 만들지 않고 root code와 필드별 message map으로만 표현한다.
    public static BaseResponse<Void> validationError(Map<String, String> errors, String traceId) {
        return new BaseResponse<>(
                false,
                CommonErrorCode.VALIDATION_FAILED.getCode(),
                CommonErrorCode.VALIDATION_FAILED.getMessage(),
                null,
                errors,
                traceId
        );
    }

    private static void validateSuccessBodyAllowed(SuccessCode successCode) {
        if (Objects.requireNonNull(successCode, "successCode").getStatus() == HttpStatus.NO_CONTENT) {
            throw new IllegalArgumentException("HTTP 204 No Content responses cannot have a body.");
        }
    }
}
