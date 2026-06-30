package org.sopt.ssingserver.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GradleTestPolicyTest {

    @Test
    void dbIntegrationTestsUseTestcontainersMysqlDependencies() throws Exception {
        String buildGradle = Files.readString(Path.of("build.gradle"));

        assertThat(buildGradle).contains("org.springframework.boot:spring-boot-testcontainers");
        assertThat(buildGradle).contains("org.testcontainers:junit-jupiter");
        assertThat(buildGradle).contains("org.testcontainers:mysql");
    }
}
