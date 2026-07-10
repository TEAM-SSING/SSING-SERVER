package org.sopt.ssingserver.global.error;

import java.util.Map;

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
