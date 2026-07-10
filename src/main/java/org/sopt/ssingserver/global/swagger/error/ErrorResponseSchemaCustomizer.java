package org.sopt.ssingserver.global.swagger.error;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.sopt.ssingserver.global.response.docs.CommonErrorResponseDocs;
import org.sopt.ssingserver.global.response.docs.ValidationErrorResponseDocs;

public class ErrorResponseSchemaCustomizer implements OpenApiCustomizer {

    @Override
    public void customise(OpenAPI openAPI) {
        Components components = openAPI.getComponents();
        if (components == null) {
            components = new Components();
            openAPI.setComponents(components);
        }

        registerSchemas(components, CommonErrorResponseDocs.class);
        registerSchemas(components, ValidationErrorResponseDocs.class);
    }

    private void registerSchemas(Components components, Class<?> schemaType) {
        Map<String, Schema> resolvedSchemas = ModelConverters.getInstance().readAll(schemaType);
        resolvedSchemas.forEach(components::addSchemas);
    }
}
