package org.sopt.ssingserver.global.swagger.success;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.lang.reflect.Constructor;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;

public class SuccessResponseExampleOperationCustomizer implements OperationCustomizer {

    private static final String NO_CONTENT = "204";
    private static final String WILDCARD_MEDIA_TYPE = "*/*";

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        ApiSuccessExamples declaration = AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getMethod(),
                ApiSuccessExamples.class
        );
        if (declaration == null) {
            return operation;
        }

        ApiResponses responses = operation.getResponses();
        if (responses == null || responses.isEmpty()) {
            return operation;
        }

        Map<String, Example> examples = createExamples(declaration);
        responses.forEach((responseCode, response) -> {
            if (isDocumentedSuccessResponse(responseCode)) {
                resolveMediaTypes(response).forEach(mediaType -> mediaType.setExamples(examples));
            }
        });
        return operation;
    }

    private boolean isDocumentedSuccessResponse(String responseCode) {
        return responseCode != null
                && responseCode.length() == 3
                && responseCode.charAt(0) == '2'
                && !NO_CONTENT.equals(responseCode);
    }

    private Iterable<MediaType> resolveMediaTypes(ApiResponse response) {
        Content content = response.getContent();
        if (content == null) {
            content = new Content();
            response.setContent(content);
        }

        if (content.isEmpty()) {
            content.addMediaType(WILDCARD_MEDIA_TYPE, new MediaType());
        }
        return content.values();
    }

    private Map<String, Example> createExamples(ApiSuccessExamples declaration) {
        Map<String, Example> examples = new LinkedHashMap<>();
        ApiSuccessExampleProvider provider = instantiateProvider(declaration.value());
        for (ApiSuccessExampleValue value : provider.examples()) {
            Example example = new Example()
                    .summary(value.summary().isBlank() ? null : value.summary())
                    .value(value.value());
            examples.put(value.name(), example);
        }
        return examples;
    }

    private ApiSuccessExampleProvider instantiateProvider(Class<? extends ApiSuccessExampleProvider> providerType) {
        try {
            Constructor<? extends ApiSuccessExampleProvider> constructor = providerType.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalArgumentException(
                    "Swagger success example provider must have a no-args constructor: " + providerType.getName(),
                    exception
            );
        }
    }
}
