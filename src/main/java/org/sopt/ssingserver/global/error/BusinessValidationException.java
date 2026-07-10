package org.sopt.ssingserver.global.error;

import java.util.Map;

// DB 상태를 확인해야 하는 검증도 요청 DTO 검증과 같은 errors 응답으로 전달하기 위한 예외.
public final class BusinessValidationException extends BusinessException {

    private final Map<String, String> errors;

    private BusinessValidationException(Map<String, String> errors) {
        super(CommonErrorCode.VALIDATION_FAILED);
        if (errors == null || errors.isEmpty()) {
            throw new IllegalArgumentException("errors must not be empty.");
        }
        this.errors = Map.copyOf(errors);
    }

    public static BusinessValidationException of(String field, String message) {
        return new BusinessValidationException(Map.of(field, message));
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
