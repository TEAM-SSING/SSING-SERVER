package org.sopt.ssingserver.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class SeedOwnershipContractTest {

    private static final Path SEED_DIRECTORY = Path.of("db/seed");
    private static final Path SCENARIO_DIRECTORY = Path.of("db/seed/scenarios");
    private static final Pattern SESSION_VARIABLE = Pattern.compile("(?<!@)@([A-Za-z0-9_]+)");
    private static final Pattern SESSION_VARIABLE_DEFINITION =
            Pattern.compile("(?m)^\\s*SET\\s+@([A-Za-z0-9_]+)\\s*=");

    @Test
    void scenario는_base가_소유한_QA계정과_강사기반정보를_다시_생성하지_않는다() throws IOException {
        try (Stream<Path> scenarioDirectories = Files.list(SCENARIO_DIRECTORY)) {
            for (Path scenarioDirectory : scenarioDirectories.filter(Files::isDirectory).toList()) {
                String seedSql = Files.readString(scenarioDirectory.resolve("seed.sql"));

                assertThat(seedSql)
                        .as("%s seed ownership", scenarioDirectory.getFileName())
                        .doesNotContain(
                                "INSERT INTO members",
                                "INSERT INTO dev_personas",
                                "INSERT INTO instructor_profiles",
                                "INSERT INTO instructor_profile_certificates",
                                "INSERT INTO instructor_price_policies",
                                "INSERT INTO instructor_matching_settings (",
                                "INSERT INTO instructor_matching_settings_lesson_levels",
                                "INSERT INTO instructor_matching_settings_available_durations"
                        );
            }
        }
    }

    @Test
    void 모든_scenario는_같은_instructor_foundation_base를_명시한다() throws IOException {
        try (Stream<Path> scenarioDirectories = Files.list(SCENARIO_DIRECTORY)) {
            for (Path scenarioDirectory : scenarioDirectories.filter(Files::isDirectory).toList()) {
                assertThat(Files.readString(scenarioDirectory.resolve("scenario.yml")))
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
                String sql = Files.readString(sqlFile);
                Set<String> referencedVariables = findVariables(sql, SESSION_VARIABLE);
                Set<String> definedVariables = findVariables(sql, SESSION_VARIABLE_DEFINITION);

                assertThat(definedVariables)
                        .as("%s locally defined MySQL session variables", sqlFile)
                        .containsAll(referencedVariables);
            }
        }
    }

    private Set<String> findVariables(String sql, Pattern pattern) {
        Set<String> variables = new HashSet<>();
        Matcher matcher = pattern.matcher(sql);

        while (matcher.find()) {
            variables.add(matcher.group(1));
        }

        return variables;
    }
}
