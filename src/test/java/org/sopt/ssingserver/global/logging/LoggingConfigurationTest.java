package org.sopt.ssingserver.global.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LoggingConfigurationTest {

    @Test
    void logbackConfigUsesPrettyLogsForLocalAndTestAndJsonForDevAndProd() throws Exception {
        String logback = Files.readString(Path.of("src/main/resources/logback-spring.xml"));

        assertThat(logback).contains("LoggingEventCompositeJsonEncoder");
        assertThat(logback).contains("request_id");
        assertThat(logback).contains("<keyValuePairs/>");
        assertThat(logback).contains("<customFields>");
        assertThat(logback).contains("<springProfile name=\"local | test\">");
        assertThat(logback).contains("<springProfile name=\"local\">");
        assertThat(logback).contains("<springProfile name=\"test\">");
        assertThat(logback).contains("<springProfile name=\"dev | prod\">");
        assertThat(logback).containsOnlyOnce("<appender name=\"CONSOLE_PRETTY\"");
        assertThat(logback).containsOnlyOnce("%highlight(%-5level)");
        assertThat(logback).containsOnlyOnce("%cyan(%logger{36})");
        assertThat(logback).containsOnlyOnce("%kvp");
        assertThat(logback).contains("<root level=\"INFO\">");
        assertThat(logback).contains("<root level=\"WARN\">");
        assertThat(logback).doesNotContain("<springProfile name=\"!test\">");
    }
}
