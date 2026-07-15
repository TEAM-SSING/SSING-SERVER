package org.sopt.ssingserver.domain.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.sopt.ssingserver.domain.notification.entity.Notification;
import org.sopt.ssingserver.domain.notification.enums.ClientApp;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false"
})
@ActiveProfiles("integration-test")
@Execution(ExecutionMode.SAME_THREAD)
class NotificationRepositoryIntegrationTest {

    private static final long MEMBER_ID = 101L;
    private static final Instant SINCE = Instant.parse("2026-07-08T00:00:00Z");
    private static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4.8"))
            .withDatabaseName("ssing_notification_repository")
            .withUsername("ssing")
            .withPassword("ssing");
    private static final Flyway FLYWAY;

    static {
        MYSQL.start();
        FLYWAY = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .validateMigrationNaming(true)
                .failOnMissingLocations(true)
                .validateOnMigrate(true)
                .baselineOnMigrate(false)
                .cleanDisabled(false)
                .load();
        FLYWAY.migrate();
    }

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM notifications WHERE member_id = ?", MEMBER_ID);
        jdbcTemplate.update("DELETE FROM members WHERE id = ?", MEMBER_ID);
    }

    @AfterAll
    static void stopMysql() {
        MYSQL.stop();
    }

    @Test
    void 최근_7일_경계와_동일한_생성시각에서_id_내림차순_커서_페이징을_적용한다() {
        insertMember();
        insertNotification(1L, SINCE.minusNanos(1));
        insertNotification(2L, SINCE);
        Instant sameCreatedAt = Instant.parse("2026-07-14T12:00:00Z");
        insertNotification(3L, sameCreatedAt);
        insertNotification(4L, sameCreatedAt);
        insertNotification(5L, Instant.parse("2026-07-14T13:00:00Z"));

        List<Notification> firstPage = notificationRepository.findFirstPage(
                MEMBER_ID,
                ClientApp.INSTRUCTOR,
                SINCE,
                PageRequest.of(0, 3)
        );

        assertThat(firstPage).extracting(Notification::getId).containsExactly(5L, 4L, 3L);

        List<Notification> nextPage = notificationRepository.findNextPage(
                MEMBER_ID,
                ClientApp.INSTRUCTOR,
                SINCE,
                firstPage.getLast().getCreatedAt(),
                firstPage.getLast().getId(),
                PageRequest.of(0, 3)
        );

        assertThat(nextPage).extracting(Notification::getId).containsExactly(2L);
    }

    private void insertMember() {
        jdbcTemplate.update(
                """
                INSERT INTO members (id, nickname, profile_image_url, role, status, created_at, updated_at)
                VALUES (?, '알림강사', NULL, 'INSTRUCTOR', 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """,
                MEMBER_ID
        );
    }

    private void insertNotification(long id, Instant createdAt) {
        jdbcTemplate.update(
                """
                INSERT INTO notifications (
                    id, member_id, client_app, type, title, body, data_json, read_at, created_at, updated_at
                ) VALUES (?, ?, 'INSTRUCTOR', 'MATCHING_OFFER_RECEIVED', '알림', '알림 본문',
                          JSON_OBJECT('deepLink', 'ssing://matching/offers/10', 'matchingOfferId', '10'),
                          NULL, ?, ?)
                """,
                id,
                MEMBER_ID,
                Timestamp.from(createdAt),
                Timestamp.from(createdAt)
        );
    }
}
