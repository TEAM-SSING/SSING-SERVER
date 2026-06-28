package org.sopt.ssingserver.global.error;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

// Spring validation 예외를 정책 응답 형식(errors: { field: message })으로 변환한다.
final class ValidationErrorMapper {

    private static final String DEFAULT_VALIDATION_MESSAGE = "요청 값이 올바르지 않습니다.";
    private static final String REQUIRED_PARAMETER_MESSAGE = "필수 요청 파라미터가 누락되었습니다.";

    private ValidationErrorMapper() {
    }

    static Map<String, String> from(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();

        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            // 같은 필드에서 여러 검증이 실패하면 먼저 발견된 메시지만 응답한다.
            errors.putIfAbsent(fieldError.getField(), resolveMessage(fieldError));
        }

        return errors;
    }

    static Map<String, String> from(HandlerMethodValidationException exception) {
        Map<String, String> errors = new LinkedHashMap<>();

        for (ParameterValidationResult result : exception.getParameterValidationResults()) {
            String parameterName = resolveParameterName(result.getMethodParameter());

            if (result instanceof ParameterErrors parameterErrors) {
                for (FieldError fieldError : parameterErrors.getFieldErrors()) {
                    errors.putIfAbsent(parameterName + "." + fieldError.getField(), resolveMessage(fieldError));
                }
                for (ObjectError globalError : parameterErrors.getGlobalErrors()) {
                    errors.putIfAbsent(parameterName, resolveMessage(globalError));
                }
                continue;
            }

            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                errors.putIfAbsent(parameterName, resolveMessage(error));
            }
        }

        return errors;
    }

    static Map<String, String> from(MissingServletRequestParameterException exception) {
        return Map.of(exception.getParameterName(), REQUIRED_PARAMETER_MESSAGE);
    }

    private static String resolveParameterName(MethodParameter methodParameter) {
        RequestParam requestParam = methodParameter.getParameterAnnotation(RequestParam.class);
        if (requestParam != null) {
            if (!requestParam.name().isBlank()) {
                return requestParam.name();
            }
            if (!requestParam.value().isBlank()) {
                return requestParam.value();
            }
        }

        PathVariable pathVariable = methodParameter.getParameterAnnotation(PathVariable.class);
        if (pathVariable != null) {
            if (!pathVariable.name().isBlank()) {
                return pathVariable.name();
            }
            if (!pathVariable.value().isBlank()) {
                return pathVariable.value();
            }
        }

        String parameterName = methodParameter.getParameterName();
        return parameterName != null ? parameterName : "parameter";
    }

    private static String resolveMessage(MessageSourceResolvable error) {
        String defaultMessage = error.getDefaultMessage();
        return defaultMessage != null ? defaultMessage : DEFAULT_VALIDATION_MESSAGE;
    }
}
