package org.sopt.ssingserver.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sopt.ssingserver.database.support.BaseSeedLoader;
import org.sopt.ssingserver.database.support.DatabaseCleaner;
import org.sopt.ssingserver.database.support.SharedMySqlDatabase;
import org.sopt.ssingserver.domain.instructor.entity.InstructorMatchingSetting;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.domain.instructor.repository.InstructorMatchingSettingRepository;
import org.sopt.ssingserver.domain.instructor.repository.projection.InstructorMatchingCandidateIdProjection;
import org.sopt.ssingserver.domain.matching.dto.result.MatchingSearchResult;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
import org.sopt.ssingserver.domain.matching.service.MatchingSearchService;
import org.sopt.ssingserver.domain.resort.repository.ResortRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC",
        "ssing.auth.jwt.issuer=ssing-instructor-negotiation-concurrency-test",
        "ssing.auth.jwt.secret=integration-test-secret-key-for-hs256-signature",
        "ssing.auth.kakao.app-id=1234",
        "ssing.matching.search-scheduler.enabled=false"
})
@ActiveProfiles("integration-test")
@Execution(ExecutionMode.SAME_THREAD)
class InstructorActiveNegotiationConcurrencyIntegrationTest {

    private static final long CONCURRENCY_TIMEOUT_SECONDS = 10L;
    private static final long LOCK_BLOCK_ASSERTION_MILLIS = 500L;

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        SharedMySqlDatabase.configureDatasource(registry);
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MatchingSearchService matchingSearchService;

    @Autowired
    private InstructorMatchingSettingRepository instructorMatchingSettingRepository;

    @Autowired
    private ResortRepository resortRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void resetDatabase() {
        DatabaseCleaner.clean(dataSource);
        BaseSeedLoader.apply(dataSource);
        runSql("db/seed/scenarios/matching-price-vivaldi/seed.sql");
    }

    // 서로 다른 소비자 요청이 동시에 같은 강사를 후보로 잡아도 setting lock 기준으로 live 협상은 한 건만 남는다.
    @Test
    void concurrentSearches는_강사당_liveNegotiation을_한건만_생성한다() throws Exception {
        long firstRequestId = createRequestedMatchingRequest("동시성소비자1");
        long secondRequestId = createRequestedMatchingRequest("동시성소비자2");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<MatchingSearchResult> first = executor.submit(() -> searchAfterBarrier(
                    firstRequestId,
                    ready,
                    start
            ));
            Future<MatchingSearchResult> second = executor.submit(() -> searchAfterBarrier(
                    secondRequestId,
                    ready,
                    start
            ));

            assertThat(ready.await(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<MatchingSearchResult> results = List.of(
                    first.get(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    second.get(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            );

            assertThat(results).extracting(MatchingSearchResult::matchingStatus)
                    .containsExactlyInAnyOrder(
                            org.sopt.ssingserver.domain.matching.enums.MatchingStatus.WAITING_FOR_INSTRUCTOR,
                            org.sopt.ssingserver.domain.matching.enums.MatchingStatus.SEARCHING
                    );
            assertThat(countLiveNegotiations()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    // 강사 수락 직후부터 결제 대기까지 모든 활성 협상 상태가 실제 JPQL에서 새 후보 선정을 막는지 검증한다.
    @ParameterizedTest(name = "{0} 활성 협상이 있으면 새 제안을 차단한다")
    @EnumSource(
            value = MatchingRequestGroupStatus.class,
            names = {"INSTRUCTOR_ACCEPTED", "CONSUMER_ACCEPTED", "PAYMENT_PENDING"}
    )
    void accepted_활성협상이_있는강사는_새제안을_받지않는다(
            MatchingRequestGroupStatus activeGroupStatus
    ) {
        long firstRequestId = createRequestedMatchingRequest("활성협상소비자1");
        MatchingSearchResult firstResult = matchingSearchService.search(firstRequestId);
        assertThat(firstResult.matchingStatus())
                .isSameAs(MatchingStatus.WAITING_FOR_INSTRUCTOR);

        jdbcTemplate.update(
                "UPDATE matching_offers SET status = 'ACCEPTED' WHERE matching_request_group_id = ?",
                firstResult.groupId()
        );
        jdbcTemplate.update(
                "UPDATE matching_request_groups SET status = ? WHERE id = ?",
                activeGroupStatus.name(),
                firstResult.groupId()
        );

        long secondRequestId = createRequestedMatchingRequest("활성협상소비자2");
        MatchingSearchResult secondResult = matchingSearchService.search(secondRequestId);

        assertThat(secondResult.matchingStatus())
                .isSameAs(MatchingStatus.SEARCHING);
        assertThat(countLiveNegotiations()).isEqualTo(1);
    }

    // 조건 변경 writer의 root lock이 후보 선정을 막고, 커밋 뒤 root/자식 컬렉션 최신값을 전달한다.
    @Test
    void 조건변경writer가_setting을_잠그면_후보잠금은_커밋뒤_최신조건을_읽는다() throws Exception {
        long instructorProfileId = findDefaultInstructorProfileId();
        CountDownLatch writerLocked = new CountDownLatch(1);
        CountDownLatch allowWriterCommit = new CountDownLatch(1);
        CountDownLatch candidateLockStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> settingUpdate = executor.submit(() -> {
                TransactionTemplate updateTransaction = new TransactionTemplate(transactionManager);
                updateTransaction.executeWithoutResult(status -> {
                    InstructorMatchingSetting setting = instructorMatchingSettingRepository
                            .findByInstructorProfileIdForUpdate(instructorProfileId)
                            .orElseThrow();
                    writerLocked.countDown();
                    await(allowWriterCommit, "Instructor matching setting commit timed out.");

                    setting.updateConditions(
                            Sport.SNOWBOARD,
                            List.of(LessonLevel.CERTIFIED),
                            List.of(180),
                            3,
                            true
                    );
                    instructorMatchingSettingRepository.saveAndFlush(setting);
                });
            });

            assertThat(writerLocked.await(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

            Future<MatchingSettingSnapshot> lockedSnapshot = executor.submit(() -> {
                TransactionTemplate searchTransaction = new TransactionTemplate(transactionManager);
                searchTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);

                return Objects.requireNonNull(searchTransaction.execute(status -> {
                    List<InstructorMatchingCandidateIdProjection> candidates = instructorMatchingSettingRepository
                            .findExposedCandidateIds(
                                    resortRepository.findByCode("VIVALDI_PARK").orElseThrow(),
                                    Sport.SKI,
                                    LessonLevel.FIRST_TIME,
                                    1,
                                    List.of(120),
                                    true
                            );
                    assertThat(candidates).hasSize(1);
                    candidateLockStarted.countDown();
                    InstructorMatchingSetting lockedSetting = instructorMatchingSettingRepository
                            .findByIdForUpdate(candidates.getFirst().getSettingId())
                            .orElseThrow();
                    return MatchingSettingSnapshot.from(lockedSetting);
                }));
            });

            assertThat(candidateLockStarted.await(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> lockedSnapshot.get(
                    LOCK_BLOCK_ASSERTION_MILLIS,
                    TimeUnit.MILLISECONDS
            )).isInstanceOf(TimeoutException.class);

            allowWriterCommit.countDown();
            settingUpdate.get(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            MatchingSettingSnapshot snapshot = lockedSnapshot.get(
                    CONCURRENCY_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            );
            assertThat(snapshot.sport()).isSameAs(Sport.SNOWBOARD);
            assertThat(snapshot.lessonLevels()).containsExactly(LessonLevel.CERTIFIED);
            assertThat(snapshot.availableDurationMinutes()).containsExactly(180);
            assertThat(snapshot.isExposed()).isTrue();
            assertThat(loadStoredSport()).isEqualTo("SNOWBOARD");
            assertThat(loadStoredLessonLevels()).containsExactly("CERTIFIED");
            assertThat(loadStoredDurationMinutes()).containsExactly(180);
        } finally {
            allowWriterCommit.countDown();
            executor.shutdownNow();
            executor.awaitTermination(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    private MatchingSearchResult searchAfterBarrier(
            long matchingRequestId,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        if (!start.await(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent matching search start timed out.");
        }
        return matchingSearchService.search(matchingRequestId);
    }

    private long findDefaultInstructorProfileId() {
        return jdbcTemplate.queryForObject(
                """
                SELECT profile.id
                FROM instructor_profiles profile
                JOIN dev_personas persona ON persona.member_id = profile.member_id
                WHERE persona.persona_key = '보법다른-유정-승인강사'
                """,
                Long.class
        );
    }

    private String loadStoredSport() {
        return jdbcTemplate.queryForObject(
                "SELECT sport FROM instructor_matching_settings",
                String.class
        );
    }

    private List<String> loadStoredLessonLevels() {
        return jdbcTemplate.queryForList(
                """
                SELECT lesson_level
                FROM instructor_matching_settings_lesson_levels
                ORDER BY lesson_level
                """,
                String.class
        );
    }

    private List<Integer> loadStoredDurationMinutes() {
        return jdbcTemplate.queryForList(
                """
                SELECT available_duration_minutes
                FROM instructor_matching_settings_available_durations
                ORDER BY available_duration_minutes
                """,
                Integer.class
        );
    }

    private void await(CountDownLatch latch, String timeoutMessage) {
        try {
            if (!latch.await(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException(timeoutMessage);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(timeoutMessage, exception);
        }
    }

    private long createRequestedMatchingRequest(String nickname) {
        long memberId = insertAndGetId(
                """
                INSERT INTO members (nickname, role, status, created_at, updated_at)
                VALUES (?, 'CONSUMER', 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """,
                nickname
        );
        long resortId = jdbcTemplate.queryForObject(
                "SELECT id FROM resorts WHERE code = 'VIVALDI_PARK'",
                Long.class
        );
        long matchingRequestId = insertAndGetId(
                """
                INSERT INTO matching_requests (
                    headcount,
                    is_equipment_ready,
                    member_id,
                    resort_id,
                    lesson_level,
                    sport,
                    status,
                    created_at,
                    updated_at
                ) VALUES (?, b'1', ?, ?, 'FIRST_TIME', 'SKI', 'REQUESTED', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """,
                1,
                memberId,
                resortId
        );
        jdbcTemplate.update(
                """
                INSERT INTO matching_requests_requested_duration_minutes (matching_request_id, duration_minutes)
                VALUES (?, ?)
                """,
                matchingRequestId,
                120
        );
        return matchingRequestId;
    }

    private long countLiveNegotiations() {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM matching_offers offer
                JOIN matching_request_groups request_group ON request_group.id = offer.matching_request_group_id
                JOIN instructor_profiles profile ON profile.id = offer.instructor_profile_id
                JOIN dev_personas persona ON persona.member_id = profile.member_id
                WHERE persona.persona_key = '보법다른-유정-승인강사'
                  AND (
                    offer.status = 'OFFERED'
                    OR (
                      offer.status = 'ACCEPTED'
                      AND request_group.status IN ('INSTRUCTOR_ACCEPTED', 'CONSUMER_ACCEPTED', 'PAYMENT_PENDING')
                    )
                  )
                """,
                Long.class
        );
    }

    private long insertAndGetId(String sql, Object... arguments) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(sql, new String[]{"id"});
            for (int index = 0; index < arguments.length; index++) {
                statement.setObject(index + 1, arguments[index]);
            }
            return statement;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private void runSql(String path) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new FileSystemResource(path));
        populator.setSqlScriptEncoding(StandardCharsets.UTF_8.name());
        populator.setContinueOnError(false);
        populator.execute(dataSource);
    }

    private record MatchingSettingSnapshot(
            Sport sport,
            Set<LessonLevel> lessonLevels,
            Set<Integer> availableDurationMinutes,
            boolean isExposed
    ) {

        private static MatchingSettingSnapshot from(InstructorMatchingSetting setting) {
            return new MatchingSettingSnapshot(
                    setting.getSport(),
                    Set.copyOf(setting.getLessonLevels()),
                    Set.copyOf(setting.getAvailableDurationMinutes()),
                    setting.isExposed()
            );
        }
    }
}
