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
        String localProfile = springProfileBlock(logback, "local");
        String testProfile = springProfileBlock(logback, "test");

        assertThat(localProfile).contains("<root level=\"INFO\">");
        assertThat(localProfile).doesNotContain("<root level=\"WARN\">");
        assertThat(localProfile).contains("<appender-ref ref=\"CONSOLE_PRETTY\"/>");
        assertThat(testProfile).contains("<root level=\"WARN\">");
        assertThat(testProfile).doesNotContain("<root level=\"INFO\">");
        assertThat(testProfile).contains("<appender-ref ref=\"CONSOLE_PRETTY\"/>");
        assertThat(logback).doesNotContain("<springProfile name=\"!test\">");
    }

    private static String springProfileBlock(String logback, String profileName) {
        String openingTag = "<springProfile name=\"" + profileName + "\">";
        int startIndex = logback.indexOf(openingTag);
        assertThat(startIndex).isNotNegative();

        int contentStartIndex = startIndex + openingTag.length();
        int endIndex = logback.indexOf("</springProfile>", contentStartIndex);
        assertThat(endIndex).isNotNegative();

        return logback.substring(contentStartIndex, endIndex);
    }
}
