package org.sopt.ssingserver.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class SeedOwnershipContractTest {

    private static final Path SEED_DIRECTORY = Path.of("db/seed");
    private static final Path SCENARIO_DIRECTORY = Path.of("db/seed/scenarios");
    private static final Set<String> BASE_OWNED_TABLES = Set.of(
            "members",
            "dev_personas",
            "instructor_profiles",
            "instructor_profile_certificates",
            "instructor_price_policies",
            "instructor_matching_settings",
            "instructor_matching_settings_lesson_levels",
            "instructor_matching_settings_available_durations"
    );
    private static final Pattern INSERT_TABLE =
            Pattern.compile("(?is)\\bINSERT\\s+INTO\\s+`?([A-Za-z0-9_]+)`?");
    private static final Pattern SESSION_VARIABLE =
            Pattern.compile("(?<!@)@`?([A-Za-z0-9_]+)`?");
    private static final Pattern SESSION_VARIABLE_DEFINITION =
            Pattern.compile("(?im)^\\s*SET\\s+@`?([A-Za-z0-9_]+)`?\\s*=");

    @Test
    void scenario는_base가_소유한_QA계정과_강사기반정보를_다시_생성하지_않는다() throws IOException {
        try (Stream<Path> scenarioDirectories = Files.list(SCENARIO_DIRECTORY)) {
            for (Path scenarioDirectory : scenarioDirectories.filter(Files::isDirectory).toList()) {
                String executableSql = maskCommentsAndStrings(
                        Files.readString(scenarioDirectory.resolve("seed.sql"))
                );

                assertThat(findVariables(executableSql, INSERT_TABLE))
                        .as("%s seed ownership", scenarioDirectory.getFileName())
                        .doesNotContainAnyElementsOf(BASE_OWNED_TABLES);
            }
        }
    }

    @Test
    void 모든_scenario는_같은_instructor_foundation_base를_명시한다() throws IOException {
        try (Stream<Path> scenarioDirectories = Files.list(SCENARIO_DIRECTORY)) {
            for (Path scenarioDirectory : scenarioDirectories.filter(Files::isDirectory).toList()) {
                List<String> baseDependencies = parseBaseDependencies(
                        Files.readString(scenarioDirectory.resolve("scenario.yml"))
                );

                assertThat(baseDependencies)
                        .as("%s base dependencies", scenarioDirectory.getFileName())
                        .contains("db/seed/base/020_instructor_foundations.sql");
            }
        }
    }

    @Test
    void 각_seed_SQL은_다른_mysql_session의_변수에_의존하지_않는다() throws IOException {
        try (Stream<Path> sqlFiles = Files.walk(SEED_DIRECTORY)) {
            for (Path sqlFile : sqlFiles
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .toList()) {
                String executableSql = maskCommentsAndStrings(Files.readString(sqlFile));
                Set<String> referencedVariables = findVariables(executableSql, SESSION_VARIABLE);
                Set<String> definedVariables = findVariables(executableSql, SESSION_VARIABLE_DEFINITION);

                assertThat(definedVariables)
                        .as("%s locally defined MySQL session variables", sqlFile)
                        .containsAll(referencedVariables);
            }
        }
    }

    @Test
    void SQL_구문_검사는_주석과_문자열을_제외하고_대소문자와_개행을_인식한다() {
        String harmlessSql = """
                -- insert into members (id) values (1);
                SELECT 'SET @missing = 1';
                SET @actual = 1;
                SELECT @actual;
                """;
        String forbiddenSql = """
                insert
                into `members` (id)
                values (1);
                """;

        String harmlessExecutableSql = maskCommentsAndStrings(harmlessSql);
        assertThat(findVariables(harmlessExecutableSql, INSERT_TABLE)).isEmpty();
        assertThat(findVariables(harmlessExecutableSql, SESSION_VARIABLE)).containsExactly("actual");
        assertThat(findVariables(harmlessExecutableSql, SESSION_VARIABLE_DEFINITION)).containsExactly("actual");
        assertThat(findVariables(maskCommentsAndStrings(forbiddenSql), INSERT_TABLE))
                .containsExactly("members");
    }

    @Test
    void YAML_의존성_검사는_주석이_아닌_실제_목록만_인식한다() {
        String yaml = """
                # base_dependencies:
                #   - db/seed/base/020_instructor_foundations.sql
                base_dependencies:
                  - db/seed/base/001_reference_data.sql
                """;

        assertThat(parseBaseDependencies(yaml))
                .containsExactly("db/seed/base/001_reference_data.sql");
    }

    private List<String> parseBaseDependencies(String yamlText) {
        Object document = new Yaml().load(yamlText);
        if (!(document instanceof Map<?, ?> values)) {
            return List.of();
        }

        Object dependencies = values.get("base_dependencies");
        if (!(dependencies instanceof List<?> dependencyList)) {
            return List.of();
        }

        return dependencyList.stream().map(Object::toString).toList();
    }

    private String maskCommentsAndStrings(String sql) {
        char[] masked = sql.toCharArray();
        boolean inLineComment = false;
        boolean inBlockComment = false;
        char quote = 0;

        for (int index = 0; index < masked.length; index++) {
            char current = masked[index];
            char next = index + 1 < masked.length ? masked[index + 1] : 0;

            if (inLineComment) {
                mask(masked, index);
                if (current == '\n' || current == '\r') {
                    inLineComment = false;
                }
                continue;
            }

            if (inBlockComment) {
                mask(masked, index);
                if (current == '*' && next == '/') {
                    mask(masked, ++index);
                    inBlockComment = false;
                }
                continue;
            }

            if (quote != 0) {
                mask(masked, index);
                if (current == '\\' && next != 0) {
                    mask(masked, ++index);
                } else if (current == quote && next == quote) {
                    mask(masked, ++index);
                } else if (current == quote) {
                    quote = 0;
                }
                continue;
            }

            if (current == '\'' || current == '"') {
                quote = current;
                mask(masked, index);
            } else if (current == '#') {
                inLineComment = true;
                mask(masked, index);
            } else if (current == '-' && next == '-' && isDashCommentStart(masked, index)) {
                inLineComment = true;
                mask(masked, index);
                mask(masked, ++index);
            } else if (current == '/' && next == '*') {
                inBlockComment = true;
                mask(masked, index);
                mask(masked, ++index);
            }
        }

        return new String(masked);
    }

    private boolean isDashCommentStart(char[] sql, int index) {
        int characterAfterDashes = index + 2;
        return characterAfterDashes >= sql.length || Character.isWhitespace(sql[characterAfterDashes]);
    }

    private void mask(char[] sql, int index) {
        if (sql[index] != '\n' && sql[index] != '\r') {
            sql[index] = ' ';
        }
    }

    private Set<String> findVariables(String sql, Pattern pattern) {
        Set<String> variables = new HashSet<>();
        Matcher matcher = pattern.matcher(sql);

        while (matcher.find()) {
            variables.add(matcher.group(1).toLowerCase(Locale.ROOT));
        }

        return variables;
    }
}
