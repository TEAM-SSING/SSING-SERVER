package org.sopt.ssingserver.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sopt.ssingserver.domain.matching.dto.result.MatchingSearchResult;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
import org.sopt.ssingserver.domain.matching.service.MatchingSearchService;
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
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

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
    private static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4.8"))
            .withDatabaseName("ssing_instructor_negotiation_concurrency")
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

    @AfterAll
    static void stopMysql() {
        MYSQL.stop();
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MatchingSearchService matchingSearchService;

    @BeforeEach
    void resetDatabase() {
        FLYWAY.clean();
        FLYWAY.migrate();
        runSql("db/seed/base/001_reference_data.sql");
        runSql("db/seed/base/010_dev_personas.sql");
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
            names = {"INSTRUCTOR_ACCEPTED", "PAYMENT_PENDING"}
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
                WHERE persona.persona_key = 'instructor-approved-default'
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
}
