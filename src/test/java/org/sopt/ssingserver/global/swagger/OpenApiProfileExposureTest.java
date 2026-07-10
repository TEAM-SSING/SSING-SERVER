package org.sopt.ssingserver.global.swagger;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class OpenApiProfileExposureTest {

    @Test
    void 기본_설정은_Swagger를_끄고_dev와_local_예시는_켠다() throws Exception {
        String baseConfig = Files.readString(Path.of("src/main/resources/application.yml"));
        String devConfig = Files.readString(Path.of("src/main/resources/application-dev.yml"));
        String localExampleConfig = Files.readString(Path.of("config/application-local.example.yml"));

        assertSwaggerExposure(baseConfig, false);
        assertSwaggerExposure(devConfig, true);
        assertSwaggerExposure(localExampleConfig, true);
    }

    private void assertSwaggerExposure(String config, boolean enabled) {
        String expected = "enabled: " + enabled;
        assertThat(config)
                .contains("springdoc:")
                .contains("api-docs:")
                .contains("swagger-ui:");
        assertThat(occurrenceCount(config, expected)).isGreaterThanOrEqualTo(2);
    }

    private long occurrenceCount(String value, String target) {
        return value.lines()
                .map(String::trim)
                .filter(target::equals)
                .count();
    }
}
