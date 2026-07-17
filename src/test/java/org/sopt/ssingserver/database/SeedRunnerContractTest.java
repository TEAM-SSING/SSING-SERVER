package org.sopt.ssingserver.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SeedRunnerContractTest {

    private static final Path RESET_CORE = Path.of("scripts/db/reset-core.sh");
    private static final Path VERIFY_ALL = Path.of("scripts/db/verify-all.sh");

    @Test
    void local_reset은_idle_base를_검증한_뒤_scenario_delta를_적용하고_검증한다() throws IOException {
        String resetCore = Files.readString(RESET_CORE);

        assertThat(resetCore).containsSubsequence(
                "apply_sql_directory \"$PROJECT_ROOT/db/seed/base\"",
                "run_mysql_file \"$PROJECT_ROOT/db/seed/verify-base.sql\"",
                "run_mysql_file \"$PROJECT_ROOT/db/seed/scenarios/$seed_target/seed.sql\"",
                "run_mysql_file \"$PROJECT_ROOT/db/seed/scenarios/$seed_target/verify.sql\"",
                "run_mysql_file \"$PROJECT_ROOT/db/seed/verify-utf8.sql\""
        );
    }

    @Test
    void verify_all은_현재_seed_target에_맞는_검증만_실행한다() throws IOException {
        String verifyAll = Files.readString(VERIFY_ALL);

        assertThat(verifyAll).containsSubsequence(
                "if is_idle_seed_target \"$seed_target\"; then",
                "run_mysql_file \"$PROJECT_ROOT/db/seed/verify-base.sql\"",
                "else",
                "run_mysql_file \"$PROJECT_ROOT/db/seed/scenarios/$seed_target/verify.sql\""
        );
    }
}
