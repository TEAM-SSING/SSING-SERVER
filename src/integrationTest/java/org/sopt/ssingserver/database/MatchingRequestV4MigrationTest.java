package org.sopt.ssingserver.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

class MatchingRequestV4MigrationTest {

    private static final long MEMBER_ID = 1L;
    private static final long RESORT_ID = 1L;
    private static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4.8"))
            .withDatabaseName("ssing_matching_request_migration")
            .withUsername("ssing")
            .withPassword("ssing");

    private static final JdbcTemplate JDBC_TEMPLATE;

    static {
        MYSQL.start();
        JDBC_TEMPLATE = new JdbcTemplate(new DriverManagerDataSource(
                MYSQL.getJdbcUrl(),
                MYSQL.getUsername(),
                MYSQL.getPassword()
        ));
    }

    @BeforeEach
    void migrateToV3AndInsertReferences() {
        flyway().clean();
        flyway(MigrationVersion.fromVersion("3")).migrate();
        insertMemberAndResort();
    }

    @AfterAll
    static void stopMysql() {
        MYSQL.stop();
    }

    @Test
    void V3의_정상_기존_요청을_보존하며_V4_단일_활성_제약을_적용한다() {
        insertMatchingRequest(1L, "REQUESTED");
        insertMatchingRequest(2L, "CANCELED");
        insertMatchingRequest(3L, "EXPIRED");
        insertMatchingRequest(4L, "FAILED");
        insertMatchingRequest(5L, "CONFIRMED");
        insertMatchingRequest(6L, "COMPLETED");
        List<MatchingRequestSnapshot> beforeMigration = matchingRequestSnapshots();

        Flyway latestFlyway = flyway();
        latestFlyway.migrate();
        latestFlyway.validate();

        assertThat(matchingRequestSnapshots()).containsExactlyElementsOf(beforeMigration);
        assertThat(JDBC_TEMPLATE.queryForObject(
                "SELECT active_negotiation_member_id FROM matching_requests WHERE id = 1",
                Long.class
        )).isEqualTo(MEMBER_ID);
        assertThat(JDBC_TEMPLATE.queryForObject(
                "SELECT COUNT(*) FROM matching_requests WHERE active_negotiation_member_id IS NULL",
                Integer.class
        )).isEqualTo(5);
        assertThat(JDBC_TEMPLATE.queryForList(
                "SELECT status FROM matching_requests WHERE active_negotiation_member_id IS NULL ORDER BY id",
                String.class
        )).containsExactly("CANCELED", "EXPIRED", "FAILED", "CONFIRMED", "COMPLETED");
        assertThat(generatedColumnExtra()).isEqualTo("STORED GENERATED");
        assertThat(constraintCount()).isEqualTo(1);
        assertThat(successfulV4HistoryCount()).isEqualTo(1);

        assertThatThrownBy(() -> insertMatchingRequest(7L, "GROUPED"))
                .isInstanceOf(DataIntegrityViolationException.class);
        insertMatchingRequest(8L, "CANCELED");
        assertThat(JDBC_TEMPLATE.queryForObject(
                "SELECT COUNT(*) FROM matching_requests",
                Integer.class
        )).isEqualTo(7);
    }

    @Test
    void V3에_활성_요청이_중복되면_V4를_부분_적용하지_않고_실패한다() {
        insertMatchingRequest(1L, "REQUESTED");
        insertMatchingRequest(2L, "MATCHED");

        Flyway latestFlyway = flyway();

        assertThatThrownBy(latestFlyway::migrate)
                .isInstanceOf(FlywayException.class);
        assertThat(JDBC_TEMPLATE.queryForObject(
                "SELECT COUNT(*) FROM matching_requests",
                Integer.class
        )).isEqualTo(2);
        assertThat(generatedColumnCount()).isZero();
        assertThat(constraintCount()).isZero();
        assertThat(successfulV4HistoryCount()).isZero();
    }

    private static Flyway flyway(MigrationVersion... targetVersion) {
        FluentConfiguration configuration = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .validateMigrationNaming(true)
                .failOnMissingLocations(true)
                .validateOnMigrate(true)
                .baselineOnMigrate(false)
                .cleanDisabled(false);
        if (targetVersion.length == 1) {
            configuration.target(targetVersion[0]);
        }
        return configuration.load();
    }

    private static void insertMemberAndResort() {
        JDBC_TEMPLATE.update(
                """
                INSERT INTO members (
                    id, nickname, profile_image_url, role, status, created_at, updated_at
                ) VALUES (?, '마이그레이션소비자', NULL, 'CONSUMER', 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """,
                MEMBER_ID
        );
        JDBC_TEMPLATE.update(
                """
                INSERT INTO resorts (
                    id, code, name, display_name, pass_fee_amount, created_at, updated_at
                ) VALUES (?, 'MIGRATION_RESORT', '마이그레이션 리조트', '마이그레이션', 0,
                          UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """,
                RESORT_ID
        );
    }

    private static void insertMatchingRequest(long id, String status) {
        JDBC_TEMPLATE.update(
                """
                INSERT INTO matching_requests (
                    id,
                    headcount,
                    is_equipment_ready,
                    canceled_at,
                    created_at,
                    expires_at,
                    matching_offer_id,
                    member_id,
                    resort_id,
                    updated_at,
                    lesson_level,
                    sport,
                    status,
                    status_reason
                ) VALUES (?, 1, b'1', NULL, UTC_TIMESTAMP(6), NULL, NULL, ?, ?, UTC_TIMESTAMP(6),
                          'FIRST_TIME', 'SKI', ?, NULL)
                """,
                id,
                MEMBER_ID,
                RESORT_ID,
                status
        );
    }

    private static int generatedColumnCount() {
        return JDBC_TEMPLATE.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'matching_requests'
                  AND column_name = 'active_negotiation_member_id'
                """,
                Integer.class
        );
    }

    private static String generatedColumnExtra() {
        return JDBC_TEMPLATE.queryForObject(
                """
                SELECT extra
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'matching_requests'
                  AND column_name = 'active_negotiation_member_id'
                """,
                String.class
        );
    }

    private static int constraintCount() {
        return JDBC_TEMPLATE.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.table_constraints
                WHERE table_schema = DATABASE()
                  AND table_name = 'matching_requests'
                  AND constraint_name = 'uk_matching_requests_active_negotiation_member'
                  AND constraint_type = 'UNIQUE'
                """,
                Integer.class
        );
    }

    private static int successfulV4HistoryCount() {
        return JDBC_TEMPLATE.queryForObject(
                """
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE version = '4'
                  AND success = 1
                """,
                Integer.class
        );
    }

    private static List<MatchingRequestSnapshot> matchingRequestSnapshots() {
        return JDBC_TEMPLATE.query(
                "SELECT id, member_id, status FROM matching_requests ORDER BY id",
                (resultSet, rowNumber) -> new MatchingRequestSnapshot(
                        resultSet.getLong("id"),
                        resultSet.getLong("member_id"),
                        resultSet.getString("status")
                )
        );
    }

    private record MatchingRequestSnapshot(long id, long memberId, String status) {
    }
}
