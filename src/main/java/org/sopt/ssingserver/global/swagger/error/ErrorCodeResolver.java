package org.sopt.ssingserver.global.swagger.error;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.sopt.ssingserver.global.error.ErrorCode;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;

public class ErrorCodeResolver {

    public List<ErrorCode> resolve(HandlerMethod handlerMethod) {
        // Controller 구현 메서드뿐 아니라 ApiDocs 인터페이스에 선언된 반복 애노테이션까지 함께 읽는다.
        Set<ApiErrorCodes> declarations = AnnotatedElementUtils.findMergedRepeatableAnnotations(
                handlerMethod.getMethod(),
                ApiErrorCodes.class
        );

        Map<String, ErrorCode> errorCodesByCode = new LinkedHashMap<>();
        for (ApiErrorCodes declaration : declarations) {
            validateEnumType(declaration.type());
            validateNames(declaration);
            for (String name : declaration.names()) {
                ErrorCode errorCode = findEnumConstant(declaration.type(), name);
                validateErrorStatus(errorCode);
                addWithoutCodeCollision(errorCodesByCode, errorCode);
            }
        }
        return List.copyOf(errorCodesByCode.values());
    }

    private void validateEnumType(Class<? extends ErrorCode> type) {
        if (!type.isEnum()) {
            throw new IllegalArgumentException("Swagger ErrorCode type must be an enum: " + type.getName());
        }
    }

    private void validateNames(ApiErrorCodes declaration) {
        if (declaration.names().length == 0) {
            throw new IllegalArgumentException(
                    "Swagger ErrorCode declaration must contain at least one enum constant: "
                            + declaration.type().getName()
            );
        }
    }

    private ErrorCode findEnumConstant(Class<? extends ErrorCode> type, String name) {
        for (ErrorCode errorCode : type.getEnumConstants()) {
            Enum<?> enumConstant = (Enum<?>) errorCode;
            if (enumConstant.name().equals(name)) {
                return errorCode;
            }
        }
        throw new IllegalArgumentException(
                "Unknown Swagger ErrorCode enum constant: " + type.getName() + "." + name
        );
    }

    private void validateErrorStatus(ErrorCode errorCode) {
        if (!errorCode.getStatus().is4xxClientError() && !errorCode.getStatus().is5xxServerError()) {
            throw new IllegalArgumentException(
                    "Swagger ErrorCode must use a 4xx or 5xx status: " + errorCode.getCode()
            );
        }
    }

    private void addWithoutCodeCollision(
            Map<String, ErrorCode> errorCodesByCode,
            ErrorCode errorCode
    ) {
        ErrorCode existing = errorCodesByCode.putIfAbsent(errorCode.getCode(), errorCode);
        if (existing != null && existing != errorCode) {
            throw new IllegalArgumentException(
                    "Duplicate Swagger ErrorCode code: " + errorCode.getCode()
            );
        }
    }
}
