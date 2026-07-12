package org.sopt.ssingserver.global.swagger;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class OpenApiProfileExposureTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer());

    @Test
    void 기본_profile은_local로_Swagger를_활성화한다() {
        assertSwaggerExposure(contextRunner, true);
    }

    @Test
    void local과_dev_profile은_Swagger를_활성화한다() {
        assertSwaggerExposure(contextRunner.withPropertyValues("spring.profiles.active=local"), true);
        assertSwaggerExposure(contextRunner.withPropertyValues("spring.profiles.active=dev"), true);
    }

    @Test
    void prod_profile은_Swagger를_비활성화한다() {
        assertSwaggerExposure(contextRunner.withPropertyValues("spring.profiles.active=prod"), false);
    }

    private void assertSwaggerExposure(ApplicationContextRunner runner, boolean enabled) {
        runner.run(context -> {
            assertThat(context.getEnvironment()
                    .getProperty("springdoc.api-docs.enabled", Boolean.class))
                    .isEqualTo(enabled);
            assertThat(context.getEnvironment()
                    .getProperty("springdoc.swagger-ui.enabled", Boolean.class))
                    .isEqualTo(enabled);
        });
    }
}
