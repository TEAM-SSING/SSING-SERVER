package org.sopt.ssingserver.database;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

class NotificationV5MigrationTest {

    private static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4.8"))
            .withDatabaseName("ssing_notification_v5_migration")
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
    void migrateToV4() {
        flyway().clean();
        flyway(MigrationVersion.fromVersion("4")).migrate();
        insertMember(1L, "CONSUMER");
        insertMember(2L, "INSTRUCTOR");
    }

    @AfterAll
    static void stopMysql() {
        MYSQL.stop();
    }

    @Test
    void V4의_지원되는_알림을_보존하며_V5_알림함_구조로_변환한다() {
        insertNotification(10L, 1L, "MATCHING_CONFIRMED");
        insertNotification(11L, 2L, "MATCHING_OFFER_RECEIVED");

        Flyway latestFlyway = flyway();
        latestFlyway.migrate();
        latestFlyway.validate();

        assertThat(JDBC_TEMPLATE.query(
                "SELECT id, client_app FROM notifications ORDER BY id",
                (resultSet, rowNumber) -> resultSet.getLong("id") + ":" + resultSet.getString("client_app")
        )).containsExactly("10:CONSUMER", "11:INSTRUCTOR");
        assertThat(columnExists("notifications", "delivery_status")).isFalse();
        assertThat(columnExists("notifications", "sent_at")).isFalse();
        assertThat(indexExists("notifications", "idx_notifications_member_created_id")).isTrue();
        assertThat(successfulV5HistoryCount()).isEqualTo(1);
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

    private static void insertMember(long id, String role) {
        JDBC_TEMPLATE.update(
                """
                INSERT INTO members (id, nickname, profile_image_url, role, status, created_at, updated_at)
                VALUES (?, ?, NULL, ?, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """,
                id,
                "알림회원" + id,
                role
        );
    }

    private static void insertNotification(long id, long memberId, String type) {
        JDBC_TEMPLATE.update(
                """
                INSERT INTO notifications (
                    id, member_id, type, title, body, data_json, delivery_status, sent_at, read_at, created_at, updated_at
                ) VALUES (?, ?, ?, '알림', '알림 본문', JSON_OBJECT(), 'SENT', UTC_TIMESTAMP(6), NULL,
                          UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """,
                id,
                memberId,
                type
        );
    }

    private static boolean columnExists(String tableName, String columnName) {
        return JDBC_TEMPLATE.queryForObject(
                """
                SELECT COUNT(*) > 0
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND column_name = ?
                """,
                Boolean.class,
                tableName,
                columnName
        );
    }

    private static boolean indexExists(String tableName, String indexName) {
        return JDBC_TEMPLATE.queryForObject(
                """
                SELECT COUNT(*) > 0
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND index_name = ?
                """,
                Boolean.class,
                tableName,
                indexName
        );
    }

    private static int successfulV5HistoryCount() {
        return JDBC_TEMPLATE.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '5' AND success = 1",
                Integer.class
        );
    }
}
