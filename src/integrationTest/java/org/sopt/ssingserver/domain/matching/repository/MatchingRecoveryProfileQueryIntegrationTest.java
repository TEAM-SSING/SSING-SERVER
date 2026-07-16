package org.sopt.ssingserver.domain.matching.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.sopt.ssingserver.database.support.DatabaseCleaner;
import org.sopt.ssingserver.database.support.SharedMySqlDatabase;
import org.sopt.ssingserver.domain.lesson.enums.LessonStatus;
import org.sopt.ssingserver.domain.lesson.repository.LessonRepository;
import org.sopt.ssingserver.domain.review.repository.ReviewRepository;
import org.sopt.ssingserver.global.config.JpaAuditingConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.transaction.BeforeTransaction;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
@Execution(ExecutionMode.SAME_THREAD)
class MatchingRecoveryProfileQueryIntegrationTest {

    private static final long INSTRUCTOR_PROFILE_ID = 11L;
    private static final long OTHER_INSTRUCTOR_PROFILE_ID = 12L;

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        SharedMySqlDatabase.configureDatasource(registry);
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private LessonRepository lessonRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeTransaction
    void cleanDatabaseBeforeTransaction() {
        DatabaseCleaner.clean(dataSource);
    }

    @Test
    void 완료강습수_평균평점_최신리뷰를_강사별_규칙대로_조회한다() {
        insertFixtures();

        long completedLessonCount = lessonRepository.countByInstructorProfileIdAndStatus(
                INSTRUCTOR_PROFILE_ID,
                LessonStatus.COMPLETED
        );
        Double averageRating = reviewRepository.findAverageRatingByInstructorProfileId(
                INSTRUCTOR_PROFILE_ID
        );
        String latestReviewContent = reviewRepository
                .findFirstByInstructorProfileIdOrderByCreatedAtDescIdDesc(INSTRUCTOR_PROFILE_ID)
                .orElseThrow()
                .getContent();

        assertThat(completedLessonCount).isEqualTo(2L);
        assertThat(averageRating).isEqualTo(4.0);
        assertThat(latestReviewContent).isEqualTo("동일 시각의 더 큰 ID 리뷰");
    }

    private void insertFixtures() {
        insertMember(101L, "복구강사", "INSTRUCTOR");
        insertMember(102L, "다른강사", "INSTRUCTOR");
        insertMember(201L, "리뷰소비자", "CONSUMER");
        jdbcTemplate.update(
                """
                INSERT INTO resorts (
                    id, code, name, display_name, pass_fee_amount, created_at, updated_at
                ) VALUES (1, 'HIGH1', '하이원리조트', '하이원', 20000, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """
        );
        insertInstructorProfile(INSTRUCTOR_PROFILE_ID, 101L, "복구강사");
        insertInstructorProfile(OTHER_INSTRUCTOR_PROFILE_ID, 102L, "다른강사");

        insertLesson(41L, 21L, 31L, INSTRUCTOR_PROFILE_ID, "COMPLETED");
        insertLesson(42L, 22L, 32L, INSTRUCTOR_PROFILE_ID, "COMPLETED");
        insertLesson(43L, 23L, 33L, INSTRUCTOR_PROFILE_ID, "CONFIRMED");
        insertLesson(44L, 24L, 34L, INSTRUCTOR_PROFILE_ID, "CANCELED");
        insertLesson(45L, 25L, 35L, OTHER_INSTRUCTOR_PROFILE_ID, "COMPLETED");

        LocalDateTime older = LocalDateTime.of(2026, 7, 10, 9, 0);
        LocalDateTime latestTie = LocalDateTime.of(2026, 7, 11, 9, 0);
        insertReview(51L, 41L, INSTRUCTOR_PROFILE_ID, 3, "오래된 리뷰", older);
        insertReview(52L, 41L, INSTRUCTOR_PROFILE_ID, 4, "동일 시각의 작은 ID 리뷰", latestTie);
        insertReview(53L, 42L, INSTRUCTOR_PROFILE_ID, 5, "동일 시각의 더 큰 ID 리뷰", latestTie);
        insertReview(
                54L,
                45L,
                OTHER_INSTRUCTOR_PROFILE_ID,
                1,
                "다른 강사의 더 최신 리뷰",
                latestTie.plusDays(1)
        );
    }

    private void insertMember(long memberId, String nickname, String role) {
        jdbcTemplate.update(
                """
                INSERT INTO members (id, nickname, profile_image_url, role, status, created_at, updated_at)
                VALUES (?, ?, NULL, ?, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """,
                memberId,
                nickname,
                role
        );
    }

    private void insertInstructorProfile(long profileId, long memberId, String realName) {
        jdbcTemplate.update(
                """
                INSERT INTO instructor_profiles (
                    id, member_id, resort_id, real_name, phone, gender, birth_date, intro,
                    career_start_date, level, certificate_type, experience, approval_status,
                    approved_at, created_at, updated_at
                ) VALUES (?, ?, 1, ?, '010-0000-0000', 'FEMALE', '1998-03-01', '소개',
                          '2020-01-01', 3, NULL, 0, 'APPROVED', UTC_TIMESTAMP(6),
                          UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """,
                profileId,
                memberId,
                realName
        );
    }

    private void insertLesson(
            long lessonId,
            long groupId,
            long offerId,
            long instructorProfileId,
            String status
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO matching_request_groups (
                    id, status, duration_minutes, created_at, updated_at
                ) VALUES (?, 'CONFIRMED', 120, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """,
                groupId
        );
        jdbcTemplate.update(
                """
                INSERT INTO matching_offers (
                    id, instructor_profile_id, matching_request_group_id, status,
                    exposed_at, responded_at, expires_at, created_at, updated_at
                ) VALUES (?, ?, ?, 'ACCEPTED', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), NULL,
                          UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """,
                offerId,
                instructorProfileId,
                groupId
        );
        jdbcTemplate.update(
                """
                INSERT INTO lessons (
                    id, instructor_profile_id, matching_offer_id, resort_id, sport, lesson_level,
                    duration_minutes, total_headcount, scheduled_at, meeting_place, status,
                    confirmed_at, started_at, completed_at, canceled_at, created_at, updated_at
                ) VALUES (?, ?, ?, 1, 'SNOWBOARD', 'FIRST_TIME', 120, 1,
                          '2026-07-12 00:00:00.000000', NULL, ?,
                          '2026-07-11 00:00:00.000000', NULL,
                          CASE WHEN ? = 'COMPLETED' THEN '2026-07-12 02:00:00.000000' ELSE NULL END,
                          CASE WHEN ? = 'CANCELED' THEN '2026-07-11 01:00:00.000000' ELSE NULL END,
                          UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """,
                lessonId,
                instructorProfileId,
                offerId,
                status,
                status,
                status
        );
    }

    private void insertReview(
            long reviewId,
            long lessonId,
            long instructorProfileId,
            int rating,
            String content,
            LocalDateTime createdAt
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO reviews (
                    id, lesson_id, member_id, instructor_profile_id, rating, content, created_at, updated_at
                ) VALUES (?, ?, 201, ?, ?, ?, ?, ?)
                """,
                reviewId,
                lessonId,
                instructorProfileId,
                rating,
                content,
                createdAt,
                createdAt
        );
    }
}
