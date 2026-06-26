package org.sopt.ssingserver.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.sopt.ssingserver.global.error.ErrorCode;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BaseResponse<T>(
        boolean success,
        String code,
        String message,
        T data,
        Map<String, String> errors,
        String traceId
) {

    // 성공 응답
    public static <T> BaseResponse<T> success(SuccessCode successCode, T data) {
        return new BaseResponse<>(true, successCode.getCode(), successCode.getMessage(), data, null, null);
    }

    // 실패 응답
    public static BaseResponse<Void> error(ErrorCode errorCode, String traceId) {
        return new BaseResponse<>(false, errorCode.getCode(), errorCode.getMessage(), null, null, traceId);
    }

    // validation 실패 응답
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
}
