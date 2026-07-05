package org.sopt.ssingserver.global.error;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.InvalidFormatException;

// 요청 입력 오류를 정책 응답 형식(errors: { field: message })으로 변환한다.
final class ValidationErrorMapper {

    private static final String DEFAULT_VALIDATION_MESSAGE = "요청 값이 올바르지 않습니다.";
    private static final String DEFAULT_FIELD = "request";
    private static final String REQUIRED_PARAMETER_MESSAGE = "필수 요청 파라미터가 누락되었습니다.";
    private static final String UNSUPPORTED_ENUM_MESSAGE = "지원하지 않는 값입니다. 허용 값: %s";

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

    static Optional<Map<String, String>> from(HttpMessageNotReadableException exception) {
        InvalidFormatException invalidFormatException = findInvalidFormatException(exception);
        if (invalidFormatException == null) {
            return Optional.empty();
        }

        Class<?> targetType = invalidFormatException.getTargetType();
        if (targetType == null || !targetType.isEnum()) {
            return Optional.empty();
        }

        String field = resolveField(invalidFormatException);
        String allowedValues = resolveAllowedValues(targetType);
        return Optional.of(Map.of(
                field,
                UNSUPPORTED_ENUM_MESSAGE.formatted(allowedValues)
        ));
    }

    private static InvalidFormatException findInvalidFormatException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof InvalidFormatException invalidFormatException) {
                return invalidFormatException;
            }
            current = current.getCause();
        }
        return null;
    }

    private static String resolveField(InvalidFormatException exception) {
        String field = exception.getPath()
                .stream()
                .map(JacksonException.Reference::getPropertyName)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("."));
        return field.isBlank() ? DEFAULT_FIELD : field;
    }

    private static String resolveAllowedValues(Class<?> enumType) {
        return Arrays.stream(enumType.getEnumConstants())
                .map(enumConstant -> ((Enum<?>) enumConstant).name())
                .collect(Collectors.joining(", "));
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
