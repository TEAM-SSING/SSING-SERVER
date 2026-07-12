package org.sopt.ssingserver.global.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.sopt.ssingserver.global.swagger.error.ErrorCodeResolver;
import org.sopt.ssingserver.global.swagger.error.ErrorResponseOperationCustomizer;
import org.sopt.ssingserver.global.swagger.error.ErrorResponseSchemaCustomizer;
import org.sopt.ssingserver.global.swagger.success.SuccessResponseExampleOperationCustomizer;
import org.sopt.ssingserver.global.swagger.success.SuccessResponseSchemaOperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "SSING API",
                version = "v1"
        )
)
@SecurityScheme(
        name = "BearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class OpenApiConfig {

    @Bean
    ErrorCodeResolver errorCodeResolver() {
        return new ErrorCodeResolver();
    }

    @Bean
    OperationCustomizer errorResponseOperationCustomizer(ErrorCodeResolver errorCodeResolver) {
        return new ErrorResponseOperationCustomizer(errorCodeResolver);
    }

    @Bean
    OperationCustomizer successResponseExampleOperationCustomizer() {
        return new SuccessResponseExampleOperationCustomizer();
    }

    @Bean
    OperationCustomizer successResponseSchemaOperationCustomizer() {
        return new SuccessResponseSchemaOperationCustomizer();
    }

    @Bean
    OpenApiCustomizer errorResponseSchemaCustomizer() {
        return new ErrorResponseSchemaCustomizer();
    }
}
