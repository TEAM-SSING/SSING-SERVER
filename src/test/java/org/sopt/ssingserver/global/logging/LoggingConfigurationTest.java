package org.sopt.ssingserver.global.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LoggingConfigurationTest {

    @Test
    void logbackConfigKeepsTestLogsMinimalAndUsesJsonOutsideTest() throws Exception {
        String logback = Files.readString(Path.of("src/main/resources/logback-spring.xml"));

        assertThat(logback).contains("LoggingEventCompositeJsonEncoder");
        assertThat(logback).contains("request_id");
        assertThat(logback).contains("<keyValuePairs/>");
        assertThat(logback).contains("<customFields>");
        assertThat(logback).contains("<springProfile name=\"test\">");
        assertThat(logback).contains("<root level=\"WARN\">");
        assertThat(logback).contains("<springProfile name=\"!test\">");
    }
}
