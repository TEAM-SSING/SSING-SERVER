package org.sopt.ssingserver.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.sopt.ssingserver.database.support.BaseSeedLoader;
import org.sopt.ssingserver.database.support.DatabaseCleaner;
import org.sopt.ssingserver.database.support.SharedMySqlDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@Execution(ExecutionMode.SAME_THREAD)
class MatchingRequestV4ConstraintIntegrationTest {

    private static final List<String> ACTIVE_STATUSES = List.of("REQUESTED", "GROUPED", "MATCHED");
    private static final List<String> HISTORICAL_STATUSES = List.of(
            "CONFIRMED",
            "COMPLETED",
            "CANCELED",
            "EXPIRED",
            "FAILED"
    );

    private final DataSource dataSource = SharedMySqlDatabase.dataSource();
    private final JdbcTemplate jdbcTemplate = SharedMySqlDatabase.jdbcTemplate();

    @BeforeEach
    void resetDatabase() {
        DatabaseCleaner.clean(dataSource);
        BaseSeedLoader.apply(dataSource);
    }

    @Test
    void V4는_서로_다른_회원으로_활성_3개와_이력_5개_상태의_제약을_한번에_검증한다() {
        for (int index = 0; index < ACTIVE_STATUSES.size(); index++) {
            String activeStatus = ACTIVE_STATUSES.get(index);
            long memberId = 10001L + index;
            insertMember(memberId, "활성상태회원" + index);
            insertMatchingRequest(memberId, activeStatus);

            assertThatThrownBy(() -> insertMatchingRequest(memberId, "REQUESTED"))
                    .as("active status %s", activeStatus)
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("uk_matching_requests_active_negotiation_member");
            assertThat(activeNegotiationCount(memberId)).isEqualTo(1);
        }

        for (int index = 0; index < HISTORICAL_STATUSES.size(); index++) {
            String historicalStatus = HISTORICAL_STATUSES.get(index);
            long memberId = 20001L + index;
            insertMember(memberId, "이력상태회원" + index);
            insertMatchingRequest(memberId, historicalStatus);
            insertMatchingRequest(memberId, historicalStatus);
            insertMatchingRequest(memberId, "REQUESTED");

            assertThat(requestCount(memberId)).as("historical status %s", historicalStatus).isEqualTo(3);
            assertThat(activeNegotiationCount(memberId)).isEqualTo(1);
        }
    }

    private void insertMember(long memberId, String nickname) {
        jdbcTemplate.update(
                """
                INSERT INTO members (id, nickname, profile_image_url, role, status, created_at, updated_at)
                VALUES (?, ?, NULL, 'CONSUMER', 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """,
                memberId,
                nickname
        );
    }

    private void insertMatchingRequest(long memberId, String status) {
        Long resortId = jdbcTemplate.queryForObject(
                "SELECT id FROM resorts WHERE code = 'HIGH1'",
                Long.class
        );
        jdbcTemplate.update(
                """
                INSERT INTO matching_requests (
                    headcount, is_equipment_ready, canceled_at, created_at, expires_at,
                    matching_offer_id, member_id, resort_id, updated_at, lesson_level,
                    sport, status, status_reason
                ) VALUES (
                    1, b'1', NULL, UTC_TIMESTAMP(6), NULL,
                    NULL, ?, ?, UTC_TIMESTAMP(6), 'BEGINNER',
                    'SKI', ?, NULL
                )
                """,
                memberId,
                resortId,
                status
        );
    }

    private int requestCount(long memberId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM matching_requests WHERE member_id = ?",
                Integer.class,
                memberId
        );
    }

    private int activeNegotiationCount(long memberId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM matching_requests
                WHERE member_id = ?
                  AND active_negotiation_member_id = ?
                """,
                Integer.class,
                memberId,
                memberId
        );
    }
}
