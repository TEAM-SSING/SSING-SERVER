package org.sopt.ssingserver.database;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.database.support.SharedMySqlDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

@Tag("legacy-migration")
class MatchingRequestParticipantV6MigrationTest {

    private static final JdbcTemplate JDBC_TEMPLATE = SharedMySqlDatabase.jdbcTemplate();

    @BeforeEach
    void migrateToV5() {
        flyway().clean();
        flyway(MigrationVersion.fromVersion("5")).migrate();
        insertMatchingRequestFixture();
    }

    @Test
    void V5의_이름없는_참가자를_보존하며_V6의_nullable_이름컬럼으로_확장한다() {
        insertLegacyParticipant(100L);

        Flyway latestFlyway = flyway();
        latestFlyway.migrate();
        latestFlyway.validate();

        assertThat(JDBC_TEMPLATE.queryForObject(
                "SELECT name FROM matching_request_participants WHERE id = 100",
                String.class
        )).isNull();
        assertThat(columnDataType()).isEqualTo("varchar");
        assertThat(columnMaximumLength()).isEqualTo(50L);
        assertThat(columnIsNullable()).isEqualTo("YES");
        assertThat(successfulV6HistoryCount()).isEqualTo(1);
        assertThat(latestFlyway.migrate().migrationsExecuted).isZero();

        insertNamedParticipant(101L, "홍길동");
        assertThat(JDBC_TEMPLATE.queryForObject(
                "SELECT name FROM matching_request_participants WHERE id = 101",
                String.class
        )).isEqualTo("홍길동");
    }

    private static Flyway flyway(MigrationVersion... targetVersion) {
        return SharedMySqlDatabase.newHistoricalMigrationFlyway(targetVersion);
    }

    private static void insertMatchingRequestFixture() {
        JDBC_TEMPLATE.update(
                """
                INSERT INTO members (id, nickname, profile_image_url, role, status, created_at, updated_at)
                VALUES (1, '요청자', NULL, 'CONSUMER', 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """
        );
        JDBC_TEMPLATE.update(
                """
                INSERT INTO resorts (id, code, name, display_name, pass_fee_amount, created_at, updated_at)
                VALUES (1, 'HIGH1', '하이원리조트', '하이원', 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """
        );
        JDBC_TEMPLATE.update(
                """
                INSERT INTO matching_requests (
                    id, member_id, resort_id, headcount, is_equipment_ready,
                    lesson_level, sport, status, created_at, updated_at
                ) VALUES (
                    10, 1, 1, 2, TRUE, 'FIRST_TIME', 'SNOWBOARD', 'REQUESTED',
                    UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
                )
                """
        );
    }

    private static void insertLegacyParticipant(long id) {
        JDBC_TEMPLATE.update(
                """
                INSERT INTO matching_request_participants (
                    id, matching_request_id, age, gender, created_at, updated_at
                ) VALUES (?, 10, 24, 'FEMALE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """,
                id
        );
    }

    private static void insertNamedParticipant(long id, String name) {
        JDBC_TEMPLATE.update(
                """
                INSERT INTO matching_request_participants (
                    id, matching_request_id, name, age, gender, created_at, updated_at
                ) VALUES (?, 10, ?, 30, 'MALE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """,
                id,
                name
        );
    }

    private static String columnDataType() {
        return JDBC_TEMPLATE.queryForObject(
                """
                SELECT data_type
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'matching_request_participants'
                  AND column_name = 'name'
                """,
                String.class
        );
    }

    private static Long columnMaximumLength() {
        return JDBC_TEMPLATE.queryForObject(
                """
                SELECT character_maximum_length
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'matching_request_participants'
                  AND column_name = 'name'
                """,
                Long.class
        );
    }

    private static String columnIsNullable() {
        return JDBC_TEMPLATE.queryForObject(
                """
                SELECT is_nullable
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'matching_request_participants'
                  AND column_name = 'name'
                """,
                String.class
        );
    }

    private static int successfulV6HistoryCount() {
        return JDBC_TEMPLATE.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '6' AND success = 1",
                Integer.class
        );
    }
}
