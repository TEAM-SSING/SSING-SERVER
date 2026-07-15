package org.sopt.ssingserver.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.database.support.SharedMySqlDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

@Tag("legacy-migration")
class NotificationV5MigrationTest {

    private static final JdbcTemplate JDBC_TEMPLATE = SharedMySqlDatabase.jdbcTemplate();

    @BeforeEach
    void migrateToV4() {
        flyway().clean();
        flyway(MigrationVersion.fromVersion("4")).migrate();
        insertMember(1L, "CONSUMER");
        insertMember(2L, "INSTRUCTOR");
        insertMember(3L, "ADMIN");
    }

    @Test
    void V4의_지원되는_세_알림_타입을_보존하며_V5_알림함_구조로_확장한다() {
        insertNotification(10L, 1L, "MATCHING_CONFIRMED");
        insertNotification(11L, 2L, "MATCHING_OFFER_RECEIVED");
        insertNotification(12L, 1L, "MATCHING_OFFER_CLOSED");
        List<NotificationLegacySnapshot> beforeMigration = notificationLegacySnapshots();

        Flyway latestFlyway = flyway();
        latestFlyway.migrate();
        latestFlyway.validate();

        assertThat(notificationLegacySnapshots()).containsExactlyElementsOf(beforeMigration);
        assertThat(JDBC_TEMPLATE.query(
                "SELECT id, client_app FROM notifications ORDER BY id",
                (resultSet, rowNumber) -> resultSet.getLong("id") + ":" + resultSet.getString("client_app")
        )).containsExactly("10:CONSUMER", "11:INSTRUCTOR", "12:CONSUMER");
        assertThat(columnDataType("notifications", "type")).isEqualTo("varchar");
        assertThat(columnIsNullable("notifications", "client_app")).isEqualTo("YES");
        assertThat(columnExists("notifications", "delivery_status")).isTrue();
        assertThat(columnExists("notifications", "sent_at")).isTrue();
        assertThat(columnDefault("notifications", "delivery_status")).isEqualTo("PENDING");
        assertThat(indexColumns("notifications", "idx_notifications_member_app_created_id"))
                .isEqualTo("member_id,client_app,created_at,id");
        assertThat(JDBC_TEMPLATE.queryForObject(
                "SELECT delivery_status FROM notifications WHERE id = 10",
                String.class
        )).isEqualTo("SENT");
        assertThat(JDBC_TEMPLATE.queryForObject(
                "SELECT sent_at IS NOT NULL FROM notifications WHERE id = 10",
                Boolean.class
        )).isTrue();
        assertThat(successfulV5HistoryCount()).isEqualTo(1);
        assertThat(latestFlyway.migrate().migrationsExecuted).isZero();

        insertNewWriterNotification(13L, 1L);
        assertThat(JDBC_TEMPLATE.queryForObject(
                "SELECT delivery_status FROM notifications WHERE id = 13",
                String.class
        )).isEqualTo("PENDING");
    }

    @Test
    void ADMIN과_지원하지_않는_legacy_type은_삭제하거나_소비자_앱으로_오분류하지_않는다() {
        insertNotification(20L, 3L, "MATCHING_CONFIRMED");
        insertNotification(21L, 1L, "LEGACY_PROMOTION");
        List<NotificationLegacySnapshot> beforeMigration = notificationLegacySnapshots();

        Flyway latestFlyway = flyway();
        latestFlyway.migrate();
        latestFlyway.validate();

        assertThat(notificationLegacySnapshots()).containsExactlyElementsOf(beforeMigration);
        assertThat(JDBC_TEMPLATE.query(
                "SELECT client_app FROM notifications ORDER BY id",
                (resultSet, rowNumber) -> resultSet.getString("client_app")
        )).containsExactly(null, null);
        assertThat(JDBC_TEMPLATE.queryForList(
                "SELECT type FROM notifications ORDER BY id",
                String.class
        )).containsExactly("MATCHING_CONFIRMED", "LEGACY_PROMOTION");
        assertThat(columnExists("notifications", "delivery_status")).isTrue();
        assertThat(columnExists("notifications", "sent_at")).isTrue();
        assertThat(successfulV5HistoryCount()).isEqualTo(1);
    }

    private static Flyway flyway(MigrationVersion... targetVersion) {
        return SharedMySqlDatabase.newHistoricalMigrationFlyway(targetVersion);
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

    private static void insertNewWriterNotification(long id, long memberId) {
        JDBC_TEMPLATE.update(
                """
                INSERT INTO notifications (
                    id, member_id, client_app, type, title, body, data_json, read_at, created_at, updated_at
                ) VALUES (?, ?, 'CONSUMER', 'MATCHING_OFFER_RECEIVED', '새 알림', '새 알림 본문',
                          JSON_OBJECT(), NULL, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """,
                id,
                memberId
        );
    }

    private static List<NotificationLegacySnapshot> notificationLegacySnapshots() {
        return JDBC_TEMPLATE.query(
                """
                SELECT id, member_id, type, title, body, data_json, delivery_status,
                       sent_at, read_at, created_at, updated_at
                FROM notifications
                ORDER BY id
                """,
                (resultSet, rowNumber) -> new NotificationLegacySnapshot(
                        resultSet.getLong("id"),
                        resultSet.getLong("member_id"),
                        resultSet.getString("type"),
                        resultSet.getString("title"),
                        resultSet.getString("body"),
                        resultSet.getString("data_json"),
                        resultSet.getString("delivery_status"),
                        resultSet.getTimestamp("sent_at"),
                        resultSet.getTimestamp("read_at"),
                        resultSet.getTimestamp("created_at"),
                        resultSet.getTimestamp("updated_at")
                )
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

    private static String columnDataType(String tableName, String columnName) {
        return JDBC_TEMPLATE.queryForObject(
                """
                SELECT data_type
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND column_name = ?
                """,
                String.class,
                tableName,
                columnName
        );
    }

    private static String columnIsNullable(String tableName, String columnName) {
        return JDBC_TEMPLATE.queryForObject(
                """
                SELECT is_nullable
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND column_name = ?
                """,
                String.class,
                tableName,
                columnName
        );
    }

    private static String columnDefault(String tableName, String columnName) {
        return JDBC_TEMPLATE.queryForObject(
                """
                SELECT column_default
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND column_name = ?
                """,
                String.class,
                tableName,
                columnName
        );
    }

    private static String indexColumns(String tableName, String indexName) {
        return JDBC_TEMPLATE.queryForObject(
                """
                SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND index_name = ?
                """,
                String.class,
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

    private record NotificationLegacySnapshot(
            long id,
            long memberId,
            String type,
            String title,
            String body,
            String dataJson,
            String deliveryStatus,
            Timestamp sentAt,
            Timestamp readAt,
            Timestamp createdAt,
            Timestamp updatedAt
    ) {
    }
}
