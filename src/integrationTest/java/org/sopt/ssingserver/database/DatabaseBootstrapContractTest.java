package org.sopt.ssingserver.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.sopt.ssingserver.database.support.BaseSeedLoader;
import org.sopt.ssingserver.database.support.DatabaseCleaner;
import org.sopt.ssingserver.database.support.SharedMySqlDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

@Execution(ExecutionMode.SAME_THREAD)
class DatabaseBootstrapContractTest {

    private final DataSource dataSource = SharedMySqlDatabase.dataSource();
    private final JdbcTemplate jdbcTemplate = SharedMySqlDatabase.jdbcTemplate();

    @Test
    void 빈_DB는_V1부터_최신까지_이동하고_데이터_초기화를_반복해도_이력과_필수값을_보존한다() {
        assertThat(SharedMySqlDatabase.initialMigrationsExecuted()).isPositive();
        SharedMySqlDatabase.flyway().validate();

        List<MigrationHistory> migrationHistory = migrationHistory();
        List<PlatformFeePolicy> platformFeePolicies = platformFeePolicies();
        List<String> applicationTables = DatabaseCleaner.applicationTableNames(dataSource);

        DatabaseCleaner.clean(dataSource);
        assertApplicationTablesAreEmpty(applicationTables);
        BaseSeedLoader.apply(dataSource);
        jdbcTemplate.update(
                """
                INSERT INTO members (id, nickname, profile_image_url, role, status, created_at, updated_at)
                VALUES (900001, '오염검증회원', NULL, 'CONSUMER', 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """
        );

        DatabaseCleaner.clean(dataSource);
        assertApplicationTablesAreEmpty(applicationTables);
        BaseSeedLoader.apply(dataSource);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM members WHERE id = 900001",
                Integer.class
        )).isZero();
        assertThat(DatabaseCleaner.applicationTableNames(dataSource)).containsExactlyElementsOf(applicationTables);
        assertThat(migrationHistory()).containsExactlyElementsOf(migrationHistory);
        assertThat(platformFeePolicies()).containsExactlyElementsOf(platformFeePolicies);
        SharedMySqlDatabase.flyway().validate();
    }

    private void assertApplicationTablesAreEmpty(List<String> applicationTables) {
        for (String table : applicationTables) {
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM `" + table.replace("`", "``") + "`",
                    Integer.class
            )).as("cleaned table %s", table).isZero();
        }
    }

    private List<MigrationHistory> migrationHistory() {
        return jdbcTemplate.query(
                """
                SELECT installed_rank, version, description, checksum, success
                FROM flyway_schema_history
                ORDER BY installed_rank
                """,
                (resultSet, rowNumber) -> new MigrationHistory(
                        resultSet.getInt("installed_rank"),
                        resultSet.getString("version"),
                        resultSet.getString("description"),
                        resultSet.getObject("checksum", Integer.class),
                        resultSet.getBoolean("success")
                )
        );
    }

    private List<PlatformFeePolicy> platformFeePolicies() {
        return jdbcTemplate.query(
                """
                SELECT fee_rate_bps, is_active
                FROM platform_fee_policies
                ORDER BY id
                """,
                (resultSet, rowNumber) -> new PlatformFeePolicy(
                        resultSet.getInt("fee_rate_bps"),
                        resultSet.getBoolean("is_active")
                )
        );
    }

    private record MigrationHistory(
            int installedRank,
            String version,
            String description,
            Integer checksum,
            boolean success
    ) {
    }

    private record PlatformFeePolicy(int feeRateBps, boolean active) {
    }
}
