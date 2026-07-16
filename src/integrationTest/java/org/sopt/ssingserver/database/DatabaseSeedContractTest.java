package org.sopt.ssingserver.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sopt.ssingserver.database.support.BaseSeedLoader;
import org.sopt.ssingserver.database.support.DatabaseCleaner;
import org.sopt.ssingserver.database.support.SharedMySqlDatabase;
import org.sopt.ssingserver.domain.auth.token.AccessTokenProvider;
import org.sopt.ssingserver.domain.matching.service.MatchingEventDispatcher;
import org.sopt.ssingserver.domain.matching.service.MatchingSearchScheduler;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC",
        "ssing.matching.search-scheduler.enabled=true",
        "ssing.auth.jwt.issuer=ssing-integration-test",
        "ssing.auth.jwt.secret=integration-test-secret-key-for-hs256-signature",
        "ssing.auth.kakao.app-id=1234"
})
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
@Execution(ExecutionMode.SAME_THREAD)
class DatabaseSeedContractTest {

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        SharedMySqlDatabase.configureDatasource(registry);
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccessTokenProvider accessTokenProvider;

    @Autowired
    private MatchingSearchScheduler matchingSearchScheduler;

    // 외부 실시간 전달만 후속 검증으로 분리하고, 요청부터 결제·강습 저장까지는 실제 빈으로 검증한다.
    @MockitoBean
    private MatchingEventDispatcher matchingEventDispatcher;

    @BeforeEach
    void resetDatabaseAndApplyBaseSeed() {
        DatabaseCleaner.clean(dataSource);
        BaseSeedLoader.apply(dataSource);
    }

    @ParameterizedTest(name = "{0} seed 계약")
    @MethodSource("scenarioKeys")
    void 모든_scenario를_초기화된_최신_schema에서_독립적으로_검증한다(String scenarioKey) throws Exception {
        assertScenarioContractFiles(scenarioKey);
        applyScenario(scenarioKey);
    }

    @Test
    void base_seed는_최종_API_계약의_리조트_11종을_제공한다() {
        List<String> resorts = jdbcTemplate.query(
                """
                SELECT CONCAT(code, '|', name, '|', display_name, '|', pass_fee_amount)
                FROM resorts
                ORDER BY code
                """,
                (resultSet, rowNumber) -> resultSet.getString(1)
        );

        assertThat(resorts).containsExactlyInAnyOrder(
                "HIGH1|하이원리조트|하이원리조트|0",
                "PHOENIX_PARK|휘닉스파크|휘닉스파크|30000",
                "VIVALDI_PARK|비발디파크|비발디파크|25000",
                "WELLI_HILLI_PARK|웰리힐리파크|웰리힐리파크|30000",
                "ELYSIAN_GANGCHON|엘리시안 강촌|엘리시안 강촌|35000",
                "OAK_VALLEY|오크밸리|오크밸리|30000",
                "ALPENSIA|알펜시아|알펜시아|30000",
                "O2_RESORT|오투리조트|오투리조트|30000",
                "KONJIAM_RESORT|곤지암리조트|곤지암리조트|35000",
                "JISAN_FOREST_RESORT|지산포레스트리조트|지산포레스트리조트|30000",
                "MUJU_DEOGYUSAN_RESORT|무주덕유산리조트|무주덕유산리조트|30000"
        );
    }

    @Test
    void 자동_Dev_배포용_base_verify_SQL을_실제_MySQL에서_검증한다() {
        runSql("db/seed/verify-base.sql");
    }

    @ParameterizedTest(name = "{0} 매칭 요청 생성")
    @ValueSource(strings = {"O2_RESORT", "MUJU_DEOGYUSAN_RESORT"})
    void base_only_신규_리조트로_매칭_요청을_생성할_수_있다(String resortCode) throws Exception {
        Long memberId = personaMemberId("대뜸GOAT-성빈-비발디가격결제-강습생");
        String consumerToken = accessTokenProvider.createAccessToken(memberId, MemberRole.CONSUMER);

        mockMvc.perform(post("/api/v1/consumer/matching-requests")
                        .header(HttpHeaders.AUTHORIZATION, bearer(consumerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resort": "%s",
                                  "sport": "SKI",
                                  "lessonLevel": "FIRST_TIME",
                                  "requestedDurationMinutes": [120],
                                  "participants": [{"age": 24, "gender": "FEMALE"}],
                                  "equipmentReady": true
                                }
                                """.formatted(resortCode)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM matching_requests request
                JOIN resorts resort ON resort.id = request.resort_id
                WHERE request.member_id = ?
                  AND resort.code = ?
                """,
                Integer.class,
                memberId,
                resortCode
        )).isEqualTo(1);
    }

    @Test
    void PM_snapshot은_원본_계약과_일치하고_명시적_scheduler_실행에서만_GROUPED로_전이한다() throws Exception {
        applyScenario("pm-full-requested-catalog");

        PmSeedSnapshotContract.assertMatches(jdbcTemplate, objectMapper);
        assertPmCatalogTransitionCounts(9, 0, 0);

        matchingSearchScheduler.runScheduledSearch();

        assertPmCatalogTransitionCounts(7, 2, 2);
        List<PmTransitionRelation> firstTransitionRelations = pmTransitionRelations();
        assertExpectedPmTransitionRelations(firstTransitionRelations);

        matchingSearchScheduler.runScheduledSearch();

        assertPmCatalogTransitionCounts(7, 2, 2);
        List<PmTransitionRelation> secondTransitionRelations = pmTransitionRelations();
        assertExpectedPmTransitionRelations(secondTransitionRelations);
        assertThat(secondTransitionRelations).containsExactlyElementsOf(firstTransitionRelations);
    }

    @Test
    void PM_dev_playground는_원본_snapshot을_바꾸지_않고_활성_요청이_없는_QA_소비자를_추가한다() throws Exception {
        applyScenario("pm-full-requested-catalog");

        PmSeedSnapshotContract.assertMatches(jdbcTemplate, objectMapper);
        runSql("db/seed/scenarios/pm-full-requested-catalog/dev-playground.sql");
        runSql("db/seed/scenarios/pm-full-requested-catalog/verify-dev-playground.sql");
        PmSeedSnapshotContract.assertMatchesIgnoringConsumerPersona(
                jdbcTemplate,
                objectMapper,
                "냅다레전드-유빈-자유QA-강습생"
        );

        Long memberId = personaMemberId("냅다레전드-유빈-자유QA-강습생");
        String consumerToken = accessTokenProvider.createAccessToken(memberId, MemberRole.CONSUMER);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM matching_requests WHERE member_id = ?",
                Integer.class,
                memberId
        )).isZero();
        assertNoActiveMatchingRequest(consumerToken);

        matchingSearchScheduler.runScheduledSearch();

        assertPmCatalogTransitionCounts(7, 2, 2);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM matching_requests
                WHERE member_id = ?
                  AND status IN ('REQUESTED', 'GROUPED', 'MATCHED')
                """,
                Integer.class,
                memberId
        )).isZero();
        assertNoActiveMatchingRequest(consumerToken);
    }

    @Test
    void 저장된_강사조건은_제안이_없어도_매칭대기_복구응답으로_조회된다() throws Exception {
        applyScenario("matching-price-vivaldi");
        String instructorToken = accessTokenProvider.createAccessToken(
                personaMemberId("보법다른-유정-비발디가격결제-강사"),
                MemberRole.INSTRUCTOR
        );

        mockMvc.perform(get("/api/v1/instructor/matching-offers")
                        .header(HttpHeaders.AUTHORIZATION, bearer(instructorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(hasKey("offerId")))
                .andExpect(jsonPath("$.data.offerId").value(nullValue()))
                .andExpect(jsonPath("$.data.matchingSetting.isExposed").value(true))
                .andExpect(jsonPath("$.data.matchingSetting.resort.code").value("VIVALDI_PARK"))
                .andExpect(jsonPath("$.data.matchingSetting.resort.displayName").value("비발디파크"))
                .andExpect(jsonPath("$.data.matchingSetting.sport").value("SKI"))
                .andExpect(jsonPath("$.data.matchingSetting.lessonLevels[0]").value("FIRST_TIME"))
                .andExpect(jsonPath("$.data.matchingSetting.lessonLevels[1]").value("BEGINNER"))
                .andExpect(jsonPath("$.data.matchingSetting.availableDurationMinutes[0]").value(120))
                .andExpect(jsonPath("$.data.matchingSetting.maxHeadcount").value(3))
                .andExpect(jsonPath("$.data.matchingSetting.equipmentReady").value(true))
                .andExpect(jsonPath("$.data.items").doesNotExist())
                .andExpect(jsonPath("$.data.currentPage").doesNotExist())
                .andExpect(jsonPath("$.data.size").doesNotExist())
                .andExpect(jsonPath("$.data.hasNext").doesNotExist())
                .andExpect(jsonPath("$.data.activeOffer").doesNotExist());
    }

    @Test
    void 단건_매칭은_scheduler_재실행_후에도_85000원으로_결제되고_강습까지_확정된다() throws Exception {
        applyScenario("matching-price-vivaldi");
        assertMandatoryAndSeedInvariants();

        Long consumerMemberId = personaMemberId("대뜸GOAT-성빈-비발디가격결제-강습생");
        Long instructorMemberId = personaMemberId("보법다른-유정-비발디가격결제-강사");
        String consumerToken = accessTokenProvider.createAccessToken(consumerMemberId, MemberRole.CONSUMER);
        String instructorToken = accessTokenProvider.createAccessToken(instructorMemberId, MemberRole.INSTRUCTOR);

        MvcResult creation = mockMvc.perform(post("/api/v1/consumer/matching-requests")
                        .header(HttpHeaders.AUTHORIZATION, bearer(consumerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scenarioRequestJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.matchingStatus").value("SEARCHING"))
                .andExpect(jsonPath("$.data.requestStatus").value("REQUESTED"))
                .andReturn();

        long matchingRequestId = responseJson(creation).at("/data/matchingRequestId").asLong();
        JsonNode offerBeforeScheduler = readInstructorOffer(instructorToken);
        assertConsumerReadback(consumerToken, matchingRequestId);
        assertActiveMatchingReadback(
                consumerToken,
                matchingRequestId,
                "WAITING_FOR_INSTRUCTOR",
                "GROUPED"
        );
        long offerIdBeforeScheduler = offerBeforeScheduler.at("/data/offerId").asLong();
        assertOfferDetailPriceReadback(instructorToken, offerIdBeforeScheduler);

        matchingSearchScheduler.runScheduledSearch();

        JsonNode offerAfterScheduler = readInstructorOffer(instructorToken);
        assertConsumerReadback(consumerToken, matchingRequestId);
        long offerId = offerAfterScheduler.at("/data/offerId").asLong();
        assertOfferDetailPriceReadback(instructorToken, offerId);
        assertThat(offerId).isEqualTo(offerIdBeforeScheduler);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM matching_offers", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM matching_offer_price_snapshots
                WHERE fee_rate_bps = 0
                  AND platform_fee_amount = 0
                  AND consumer_total_amount = 60000
                  AND resort_pass_fee_amount = 25000
                  AND instructor_settlement_amount = 60000
                  AND total_payment_amount = 85000
                """,
                Integer.class
        )).isEqualTo(1);

        acceptInstructorOffer(instructorToken, offerId);
        assertActiveConfirmationReadback(
                consumerToken,
                matchingRequestId,
                0,
                1
        );
        acceptConsumerConfirmation(consumerToken, matchingRequestId);
        assertActivePaymentReadback(
                consumerToken,
                matchingRequestId,
                0,
                1
        );
        long lessonId = completeConsumerPayment(consumerToken, matchingRequestId);

        assertConsumerConfirmedReadback(consumerToken, matchingRequestId, lessonId);
        assertNoActiveMatchingRequest(consumerToken);
        assertConsumerHomeLessonReadback(consumerToken, lessonId);
        assertSingleRequestPaymentAndLesson(matchingRequestId, offerId, lessonId);
    }

    @Test
    void 후보가_없는_요청은_scheduler_실행_후에도_SEARCHING을_유지한다() throws Exception {
        applyScenario("matching-no-candidate-alpensia");
        String consumerToken = accessTokenProvider.createAccessToken(
                personaMemberId("대뜸GOAT-성빈-비발디가격결제-강습생"),
                MemberRole.CONSUMER
        );

        long matchingRequestId = createSearchingRequest(
                consumerToken,
                "db/seed/scenarios/matching-no-candidate-alpensia/request.json"
        );

        matchingSearchScheduler.runScheduledSearch();

        mockMvc.perform(get("/api/v1/consumer/matching-requests/{matchingRequestId}", matchingRequestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matchingStatus").value("SEARCHING"))
                .andExpect(jsonPath("$.data.requestStatus").value("REQUESTED"));
        assertActiveMatchingReadback(
                consumerToken,
                matchingRequestId,
                "SEARCHING",
                "REQUESTED"
        );
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM matching_request_groups", Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM matching_offers", Integer.class))
                .isZero();
    }

    @Test
    void MATCHED인데_결제자식만_만료된_이상데이터는_active에서_NONE으로_닫는다() throws Exception {
        applyScenario("matching-price-vivaldi");
        Long consumerMemberId = personaMemberId("대뜸GOAT-성빈-비발디가격결제-강습생");
        Long instructorMemberId = personaMemberId("보법다른-유정-비발디가격결제-강사");
        String consumerToken = accessTokenProvider.createAccessToken(consumerMemberId, MemberRole.CONSUMER);
        String instructorToken = accessTokenProvider.createAccessToken(instructorMemberId, MemberRole.INSTRUCTOR);

        MvcResult creation = mockMvc.perform(post("/api/v1/consumer/matching-requests")
                        .header(HttpHeaders.AUTHORIZATION, bearer(consumerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scenarioRequestJson()))
                .andExpect(status().isCreated())
                .andReturn();
        long matchingRequestId = responseJson(creation).at("/data/matchingRequestId").asLong();
        long offerId = readInstructorOffer(instructorToken).at("/data/offerId").asLong();
        acceptInstructorOffer(instructorToken, offerId);
        acceptConsumerConfirmation(consumerToken, matchingRequestId);

        jdbcTemplate.update(
                "UPDATE matching_request_payments SET status = 'EXPIRED' WHERE matching_request_id = ?",
                matchingRequestId
        );

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM matching_requests WHERE id = ?",
                String.class,
                matchingRequestId
        )).isEqualTo("MATCHED");
        assertNoActiveMatchingRequest(consumerToken);
    }

    @Test
    void 한_소비자는_기존_활성_요청을_취소한_뒤에만_다음_요청을_생성한다() throws Exception {
        applyScenario("matching-multi-request-oak");
        Long memberId = personaMemberId("도파민풀충-나현-오크다중요청-강습생");
        String consumerToken = accessTokenProvider.createAccessToken(memberId, MemberRole.CONSUMER);
        List<String> requestNames = List.of("a", "b", "c", "d");
        Long[] matchingRequestIds = new Long[requestNames.size()];

        for (int index = 0; index < requestNames.size(); index++) {
            String requestName = requestNames.get(index);
            matchingRequestIds[index] = createSearchingRequest(
                    consumerToken,
                    "db/seed/scenarios/matching-multi-request-oak/request-007-" + requestName + ".json"
            );
            assertActiveMatchingReadback(
                    consumerToken,
                    matchingRequestIds[index],
                    "SEARCHING",
                    "REQUESTED"
            );
            if (index < requestNames.size() - 1) {
                String nextRequestName = requestNames.get(index + 1);
                assertDuplicateMatchingRequestRejected(
                        consumerToken,
                        "db/seed/scenarios/matching-multi-request-oak/request-007-"
                                + nextRequestName + ".json"
                );
                cancelMatchingRequest(consumerToken, matchingRequestIds[index]);
                assertNoActiveMatchingRequest(consumerToken);
            }
        }

        matchingSearchScheduler.runScheduledSearch();

        for (int index = 0; index < matchingRequestIds.length; index++) {
            String expectedStatus = index == matchingRequestIds.length - 1 ? "REQUESTED" : "CANCELED";
            String expectedMatchingStatus = index == matchingRequestIds.length - 1 ? "SEARCHING" : "CANCELED";
            mockMvc.perform(get(
                            "/api/v1/consumer/matching-requests/{matchingRequestId}",
                            matchingRequestIds[index]
                    )
                            .header(HttpHeaders.AUTHORIZATION, bearer(consumerToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.matchingStatus").value(expectedMatchingStatus))
                    .andExpect(jsonPath("$.data.requestStatus").value(expectedStatus));
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM matching_requests WHERE member_id = ?",
                Integer.class,
                memberId
        )).isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM matching_request_participants participant
                JOIN matching_requests request ON request.id = participant.matching_request_id
                WHERE request.member_id = ?
                """,
                Integer.class,
                memberId
        )).isEqualTo(16);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM matching_requests
                WHERE member_id = ?
                  AND status IN ('REQUESTED', 'GROUPED', 'MATCHED')
                """,
                Integer.class,
                memberId
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM matching_requests WHERE member_id = ? AND status = 'CANCELED'",
                Integer.class,
                memberId
        )).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM matching_request_groups",
                Integer.class
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM matching_offers",
                Integer.class
        )).isZero();
    }

    @Test
    void 같은_소비자의_동시_생성_요청은_하나만_201이고_나머지는_409다() throws Exception {
        applyScenario("matching-multi-request-oak");
        Long memberId = personaMemberId("도파민풀충-나현-오크다중요청-강습생");
        String consumerToken = accessTokenProvider.createAccessToken(memberId, MemberRole.CONSUMER);
        String requestBody = requestJson(
                "db/seed/scenarios/matching-multi-request-oak/request-007-a.json"
        );
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(2);

        Callable<Integer> createRequest = () -> {
            ready.countDown();
            start.await(5, TimeUnit.SECONDS);
            return mockMvc.perform(post("/api/v1/consumer/matching-requests")
                            .header(HttpHeaders.AUTHORIZATION, bearer(consumerToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andReturn()
                    .getResponse()
                    .getStatus();
        };

        try {
            Future<Integer> first = executorService.submit(createRequest);
            Future<Integer> second = executorService.submit(createRequest);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
            )).containsExactlyInAnyOrder(201, 409);
        } finally {
            executorService.shutdownNow();
        }

        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM matching_requests
                WHERE member_id = ?
                  AND status IN ('REQUESTED', 'GROUPED', 'MATCHED')
                """,
                Integer.class,
                memberId
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM matching_request_participants participant
                JOIN matching_requests request ON request.id = participant.matching_request_id
                WHERE request.member_id = ?
                """,
                Integer.class,
                memberId
        )).isEqualTo(5);
    }

    private JsonNode readInstructorOffer(String instructorToken) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/instructor/matching-offers")
                        .header(HttpHeaders.AUTHORIZATION, bearer(instructorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.offerId").isNumber())
                .andExpect(jsonPath("$.data.matchingSetting").exists())
                .andExpect(jsonPath("$.data.items").doesNotExist())
                .andReturn();
        return responseJson(result);
    }

    private void assertConsumerReadback(String consumerToken, long matchingRequestId) throws Exception {
        mockMvc.perform(get("/api/v1/consumer/matching-requests/{matchingRequestId}", matchingRequestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matchingRequestId").value(matchingRequestId))
                .andExpect(jsonPath("$.data.matchingStatus").value("WAITING_FOR_INSTRUCTOR"))
                .andExpect(jsonPath("$.data.requestStatus").value("GROUPED"))
                .andExpect(jsonPath("$.data.offerStatus").value("OFFERED"))
                .andExpect(jsonPath("$.data.recoveryState").doesNotExist());
    }

    private void assertActiveMatchingReadback(
            String consumerToken,
            long matchingRequestId,
            String matchingStatus,
            String requestStatus
    ) throws Exception {
        ResultActions result = mockMvc.perform(get("/api/v1/consumer/matching-requests/active")
                        .header(HttpHeaders.AUTHORIZATION, bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recoveryState").value("ACTIVE"))
                .andExpect(jsonPath("$.data.matchingRequestId").value(matchingRequestId))
                .andExpect(jsonPath("$.data.matchingStatus").value(matchingStatus))
                .andExpect(jsonPath("$.data.requestStatus").value(requestStatus))
                .andExpect(jsonPath("$.data.lessonSummary").doesNotExist())
                .andExpect(jsonPath("$.data.instructorProfile").doesNotExist())
                .andExpect(jsonPath("$.data.progressSummary").doesNotExist())
                .andExpect(jsonPath("$.data.priceSummary").doesNotExist())
                .andExpect(jsonPath("$.data.paymentStatus").doesNotExist())
                .andExpect(jsonPath("$.data.expiresAt").doesNotExist())
                .andExpect(jsonPath("$.data.lessonId").doesNotExist());
        assertActiveRequestSummary(result, matchingRequestId);
        if ("SEARCHING".equals(matchingStatus)) {
            result.andExpect(jsonPath("$.data.groupId").doesNotExist())
                    .andExpect(jsonPath("$.data.groupStatus").doesNotExist())
                    .andExpect(jsonPath("$.data.itemStatus").doesNotExist())
                    .andExpect(jsonPath("$.data.offerStatus").doesNotExist());
        }
    }

    private void assertActiveConfirmationReadback(
            String consumerToken,
            long matchingRequestId,
            int acceptedRequesterCount,
            int totalRequesterCount
    ) throws Exception {
        ResultActions result = mockMvc.perform(get("/api/v1/consumer/matching-requests/active")
                        .header(HttpHeaders.AUTHORIZATION, bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recoveryState").value("ACTIVE"))
                .andExpect(jsonPath("$.data.matchingRequestId").value(matchingRequestId))
                .andExpect(jsonPath("$.data.matchingStatus").value("WAITING_FOR_CONFIRMATION"))
                .andExpect(jsonPath("$.data.requestStatus").value("MATCHED"))
                .andExpect(jsonPath("$.data.progressSummary.acceptedRequesterCount")
                        .value(acceptedRequesterCount))
                .andExpect(jsonPath("$.data.progressSummary.totalRequesterCount")
                        .value(totalRequesterCount))
                .andExpect(jsonPath("$.data.progressSummary.paidRequesterCount").doesNotExist())
                .andExpect(jsonPath("$.data.paymentStatus").doesNotExist())
                .andExpect(jsonPath("$.data.priceSummary.totalPaymentAmount").value(85_000));
        assertActiveRequestSummary(result, matchingRequestId);
        assertActiveLessonAndInstructorSummary(result, matchingRequestId);
    }

    private void assertActivePaymentReadback(
            String consumerToken,
            long matchingRequestId,
            int paidRequesterCount,
            int totalRequesterCount
    ) throws Exception {
        ResultActions result = mockMvc.perform(get("/api/v1/consumer/matching-requests/active")
                        .header(HttpHeaders.AUTHORIZATION, bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recoveryState").value("ACTIVE"))
                .andExpect(jsonPath("$.data.matchingRequestId").value(matchingRequestId))
                .andExpect(jsonPath("$.data.matchingStatus").value("PAYMENT_PENDING"))
                .andExpect(jsonPath("$.data.requestStatus").value("MATCHED"))
                .andExpect(jsonPath("$.data.paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.progressSummary.acceptedRequesterCount").doesNotExist())
                .andExpect(jsonPath("$.data.progressSummary.totalRequesterCount")
                        .value(totalRequesterCount))
                .andExpect(jsonPath("$.data.progressSummary.paidRequesterCount")
                        .value(paidRequesterCount))
                .andExpect(jsonPath("$.data.priceSummary.totalPaymentAmount").value(85_000));
        assertActiveRequestSummary(result, matchingRequestId);
        assertActiveLessonAndInstructorSummary(result, matchingRequestId);
    }

    private void assertActiveRequestSummary(
            ResultActions result,
            long matchingRequestId
    ) throws Exception {
        Map<String, Object> expected = jdbcTemplate.queryForMap(
                """
                SELECT resort.code,
                       resort.display_name,
                       request.sport,
                       request.lesson_level,
                       request.headcount
                FROM matching_requests request
                JOIN resorts resort ON resort.id = request.resort_id
                WHERE request.id = ?
                """,
                matchingRequestId
        );
        result.andExpect(jsonPath("$.data.requestSummary.resort.code").value(expected.get("code")))
                .andExpect(jsonPath("$.data.requestSummary.resort.displayName")
                        .value(expected.get("display_name")))
                .andExpect(jsonPath("$.data.requestSummary.sport").value(expected.get("sport").toString()))
                .andExpect(jsonPath("$.data.requestSummary.lessonLevel")
                        .value(expected.get("lesson_level").toString()))
                .andExpect(jsonPath("$.data.requestSummary.headcount").value(expected.get("headcount")));
    }

    private void assertActiveLessonAndInstructorSummary(
            ResultActions result,
            long matchingRequestId
    ) throws Exception {
        Map<String, Object> expectedLesson = jdbcTemplate.queryForMap(
                """
                SELECT request_group.duration_minutes,
                       SUM(group_request.headcount) AS total_headcount
                FROM matching_request_group_items item
                JOIN matching_request_groups request_group
                  ON request_group.id = item.matching_request_group_id
                JOIN matching_requests group_request
                  ON group_request.id = item.matching_request_id
                WHERE item.matching_request_group_id = (
                    SELECT current_item.matching_request_group_id
                    FROM matching_request_group_items current_item
                    WHERE current_item.matching_request_id = ?
                    ORDER BY current_item.id DESC
                    LIMIT 1
                )
                GROUP BY request_group.id, request_group.duration_minutes
                """,
                matchingRequestId
        );
        result.andExpect(jsonPath("$.data.lessonSummary.durationMinutes")
                        .value(expectedLesson.get("duration_minutes")))
                .andExpect(jsonPath("$.data.lessonSummary.totalHeadcount")
                        .value(expectedLesson.get("total_headcount")))
                .andExpect(jsonPath("$.data.lessonSummary.startType").value("IMMEDIATE"))
                .andExpect(jsonPath("$.data.instructorProfile.instructorId").isNumber())
                .andExpect(jsonPath("$.data.instructorProfile.name").isNotEmpty())
                .andExpect(jsonPath("$.data.instructorProfile.gender").isNotEmpty())
                .andExpect(jsonPath("$.data.instructorProfile.birthYear").isNumber())
                .andExpect(jsonPath("$.data.instructorProfile.level").isNumber())
                .andExpect(jsonPath("$.data.instructorProfile.careerYears").isNumber())
                .andExpect(jsonPath("$.data.instructorProfile.completedLessonCount").value(0))
                .andExpect(jsonPath("$.data.instructorProfile.introduction").isNotEmpty())
                .andExpect(jsonPath("$.data.instructorProfile.certificateTypes").isArray())
                .andExpect(jsonPath("$.data.instructorProfile.averageRating").doesNotExist())
                .andExpect(jsonPath("$.data.instructorProfile.latestReview").doesNotExist())
                .andExpect(jsonPath("$.data.instructorProfile.phone").doesNotExist())
                .andExpect(jsonPath("$.data.expiresAt").doesNotExist())
                .andExpect(jsonPath("$.data.lessonId").doesNotExist());
    }

    private void assertNoActiveMatchingRequest(String consumerToken) throws Exception {
        mockMvc.perform(get("/api/v1/consumer/matching-requests/active")
                        .header(HttpHeaders.AUTHORIZATION, bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.recoveryState").value("NONE"))
                .andExpect(jsonPath("$.data.matchingRequestId").doesNotExist())
                .andExpect(jsonPath("$.data.matchingStatus").doesNotExist());
    }

    private void assertConsumerHomeLessonReadback(String consumerToken, long lessonId) throws Exception {
        mockMvc.perform(get("/api/v1/consumer/home")
                        .header(HttpHeaders.AUTHORIZATION, bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lessonCards.length()").value(1))
                .andExpect(jsonPath("$.data.lessonCards[0].lessonId").value(lessonId))
                .andExpect(jsonPath("$.data.lessonCards[0].displayStatus").value("CONFIRMED"));
    }

    private void assertOfferDetailPriceReadback(String instructorToken, long offerId) throws Exception {
        mockMvc.perform(get("/api/v1/instructor/matching-offers/{offerId}", offerId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(instructorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recoveryState").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.offerId").value(offerId))
                .andExpect(jsonPath("$.data.offerStatus").value("OFFERED"))
                .andExpect(jsonPath("$.data.lessonSummary.resort.code").value("VIVALDI_PARK"))
                .andExpect(jsonPath("$.data.priceSummary.instructorSettlementAmount").value(60_000))
                .andExpect(jsonPath("$.data.priceSummary.resortPassFeeAmount").doesNotExist());
    }

    private void acceptInstructorOffer(String instructorToken, long offerId) throws Exception {
        mockMvc.perform(patch("/api/v1/instructor/matching-offers/{offerId}", offerId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(instructorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"ACCEPTED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.offerId").value(offerId))
                .andExpect(jsonPath("$.data.offerStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.groupStatus").value("INSTRUCTOR_ACCEPTED"));
    }

    private void acceptConsumerConfirmation(String consumerToken, long matchingRequestId) throws Exception {
        mockMvc.perform(patch(
                        "/api/v1/consumer/matching-requests/{matchingRequestId}/confirmation",
                        matchingRequestId
                )
                        .header(HttpHeaders.AUTHORIZATION, bearer(consumerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"ACCEPTED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matchingStatus").value("PAYMENT_PENDING"))
                .andExpect(jsonPath("$.data.confirmationStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.groupStatus").value("PAYMENT_PENDING"))
                .andExpect(jsonPath("$.data.priceSummary.lessonPriceAmount").value(60_000))
                .andExpect(jsonPath("$.data.priceSummary.resortPassFeeAmount").value(25_000))
                .andExpect(jsonPath("$.data.priceSummary.totalPaymentAmount").value(85_000));
    }

    private long completeConsumerPayment(String consumerToken, long matchingRequestId) throws Exception {
        MvcResult result = mockMvc.perform(post(
                        "/api/v1/consumer/matching-requests/{matchingRequestId}/payment",
                        matchingRequestId
                )
                        .header(HttpHeaders.AUTHORIZATION, bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matchingStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.paymentStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.groupStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.paidCount").value(1))
                .andExpect(jsonPath("$.data.requiredCount").value(1))
                .andExpect(jsonPath("$.data.lessonId").isNumber())
                .andReturn();
        return responseJson(result).at("/data/lessonId").asLong();
    }

    private void assertConsumerConfirmedReadback(
            String consumerToken,
            long matchingRequestId,
            long lessonId
    ) throws Exception {
        mockMvc.perform(get("/api/v1/consumer/matching-requests/{matchingRequestId}", matchingRequestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matchingStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.requestStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.paymentStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.lessonId").value(lessonId))
                .andExpect(jsonPath("$.data.priceSummary.lessonPriceAmount").value(60_000))
                .andExpect(jsonPath("$.data.priceSummary.resortPassFeeAmount").value(25_000))
                .andExpect(jsonPath("$.data.priceSummary.totalPaymentAmount").value(85_000));
    }

    private void assertSingleRequestPaymentAndLesson(
            long matchingRequestId,
            long offerId,
            long lessonId
    ) {
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM matching_request_price_snapshots
                WHERE matching_request_id = ?
                  AND lesson_price_amount = 60000
                  AND resort_pass_fee_amount = 25000
                  AND consumer_payment_amount = 85000
                """,
                Integer.class,
                matchingRequestId
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM matching_request_payments
                WHERE matching_request_id = ?
                  AND matching_offer_id = ?
                  AND amount = 85000
                  AND status = 'COMPLETED'
                  AND paid_at IS NOT NULL
                """,
                Integer.class,
                matchingRequestId,
                offerId
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM lessons
                WHERE id = ?
                  AND matching_offer_id = ?
                  AND total_headcount = 1
                  AND status = 'CONFIRMED'
                  AND confirmed_at = scheduled_at
                """,
                Integer.class,
                lessonId,
                offerId
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM lesson_participants
                WHERE lesson_id = ?
                  AND matching_request_id = ?
                """,
                Integer.class,
                lessonId,
                matchingRequestId
        )).isEqualTo(1);
    }

    private JsonNode responseJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private String scenarioRequestJson() throws Exception {
        return requestJson("db/seed/scenarios/matching-price-vivaldi/request.json");
    }

    private String requestJson(String path) throws Exception {
        return new FileSystemResource(path)
                .getContentAsString(StandardCharsets.UTF_8);
    }

    private long createSearchingRequest(String consumerToken, String requestPath) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/consumer/matching-requests")
                        .header(HttpHeaders.AUTHORIZATION, bearer(consumerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(requestPath)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.matchingStatus").value("SEARCHING"))
                .andExpect(jsonPath("$.data.requestStatus").value("REQUESTED"))
                .andReturn();
        return responseJson(result).at("/data/matchingRequestId").asLong();
    }

    private void assertDuplicateMatchingRequestRejected(String consumerToken, String requestPath) throws Exception {
        mockMvc.perform(post("/api/v1/consumer/matching-requests")
                        .header(HttpHeaders.AUTHORIZATION, bearer(consumerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(requestPath)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MATCHING_REQUEST_ALREADY_EXISTS"));
    }

    private void cancelMatchingRequest(String consumerToken, Long matchingRequestId) throws Exception {
        mockMvc.perform(post(
                        "/api/v1/consumer/matching-requests/{matchingRequestId}/cancellation",
                        matchingRequestId
                )
                        .header(HttpHeaders.AUTHORIZATION, bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestStatus").value("CANCELED"));
    }

    private static Stream<String> scenarioKeys() throws Exception {
        try (Stream<Path> scenarioDirectories = Files.list(Path.of("db/seed/scenarios"))) {
            return scenarioDirectories
                    .filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .toList()
                    .stream();
        }
    }

    private void assertScenarioContractFiles(String scenarioKey) throws Exception {
        Path scenarioDirectory = Path.of("db/seed/scenarios", scenarioKey);
        Path scenarioDefinition = scenarioDirectory.resolve("scenario.yml");

        assertThat(scenarioDirectory.resolve("seed.sql")).exists();
        assertThat(scenarioDirectory.resolve("verify.sql")).exists();
        assertThat(scenarioDefinition).exists();
        assertThat(Files.readString(scenarioDefinition))
                .contains("scenario_key: " + scenarioKey);
    }

    private void applyScenario(String scenarioKey) {
        runSql("db/seed/scenarios/" + scenarioKey + "/seed.sql");
        runSql("db/seed/scenarios/" + scenarioKey + "/verify.sql");
        runSql("db/seed/verify-utf8.sql");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private Long personaMemberId(String personaKey) {
        return jdbcTemplate.queryForObject(
                "SELECT member_id FROM dev_personas WHERE persona_key = ?",
                Long.class,
                personaKey
        );
    }

    private void assertMandatoryAndSeedInvariants() {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 0",
                Integer.class
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'lesson_start_confirmations'
                  AND column_name = 'status'
                """,
                Integer.class
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM platform_fee_policies WHERE is_active = 1 AND fee_rate_bps = 0",
                Integer.class
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT pass_fee_amount FROM resorts WHERE code = 'VIVALDI_PARK'",
                Integer.class
        )).isEqualTo(25_000);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM instructor_matching_settings setting
                JOIN instructor_profiles profile ON profile.id = setting.instructor_profile_id
                JOIN dev_personas persona ON persona.member_id = profile.member_id
                WHERE persona.persona_key = '보법다른-유정-비발디가격결제-강사'
                  AND setting.is_exposed = 1
                  AND setting.is_equipment_ready = 1
                """,
                Integer.class
        )).isEqualTo(1);
    }

    private void assertPmCatalogTransitionCounts(int requested, int groups, int offers) {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM matching_requests WHERE status = 'REQUESTED'",
                Integer.class
        )).isEqualTo(requested);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM matching_request_groups",
                Integer.class
        )).isEqualTo(groups);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM matching_offers",
                Integer.class
        )).isEqualTo(offers);
    }

    private void assertExpectedPmTransitionRelations(List<PmTransitionRelation> relations) {
        assertThat(relations).hasSize(2);
        assertThat(relations)
                .extracting(PmTransitionRelation::requestKey)
                .containsExactlyInAnyOrder(
                        "폭룡적-예지-하이원초급2인-강습생:HIGH1:SKI:BEGINNER",
                        "대뜸GOAT-성빈-비발디가격결제-강습생:VIVALDI_PARK:SKI:FIRST_TIME"
                );
        assertThat(relations).allSatisfy(relation -> {
            assertThat(relation.groupId()).isPositive();
            assertThat(relation.groupStatus()).isEqualTo("EXPOSED");
            assertThat(relation.groupItemCount()).isEqualTo(1);
            assertThat(relation.groupItemId()).isPositive();
            assertThat(relation.groupItemStatus()).isEqualTo("NOT_REQUESTED");
            assertThat(relation.requestId()).isPositive();
            assertThat(relation.requestStatus()).isEqualTo("GROUPED");
            assertThat(relation.offerCount()).isEqualTo(1);
            assertThat(relation.offerId()).isPositive();
            assertThat(relation.offerStatus()).isEqualTo("OFFERED");
        });
        assertThat(relations).extracting(PmTransitionRelation::groupId).doesNotHaveDuplicates();
        assertThat(relations).extracting(PmTransitionRelation::groupItemId).doesNotHaveDuplicates();
        assertThat(relations).extracting(PmTransitionRelation::requestId).doesNotHaveDuplicates();
        assertThat(relations).extracting(PmTransitionRelation::offerId).doesNotHaveDuplicates();
    }

    private List<PmTransitionRelation> pmTransitionRelations() {
        return jdbcTemplate.query(
                """
                SELECT group_table.id AS group_id,
                       CAST(group_table.status AS CHAR) AS group_status,
                       COUNT(DISTINCT group_item.id) AS group_item_count,
                       MIN(group_item.id) AS group_item_id,
                       MIN(CAST(group_item.status AS CHAR)) AS group_item_status,
                       MIN(request.id) AS request_id,
                       MIN(CONCAT(
                           persona.persona_key, ':', resort.code, ':', request.sport, ':', request.lesson_level
                       )) AS request_key,
                       MIN(CAST(request.status AS CHAR)) AS request_status,
                       COUNT(DISTINCT offer.id) AS offer_count,
                       MIN(offer.id) AS offer_id,
                       MIN(CAST(offer.status AS CHAR)) AS offer_status
                FROM matching_request_groups group_table
                LEFT JOIN matching_request_group_items group_item
                  ON group_item.matching_request_group_id = group_table.id
                LEFT JOIN matching_requests request
                  ON request.id = group_item.matching_request_id
                LEFT JOIN dev_personas persona
                  ON persona.member_id = request.member_id
                LEFT JOIN resorts resort
                  ON resort.id = request.resort_id
                LEFT JOIN matching_offers offer
                  ON offer.matching_request_group_id = group_table.id
                GROUP BY group_table.id, group_table.status
                ORDER BY group_table.id
                """,
                (resultSet, rowNumber) -> new PmTransitionRelation(
                        resultSet.getLong("group_id"),
                        resultSet.getString("group_status"),
                        resultSet.getInt("group_item_count"),
                        resultSet.getObject("group_item_id", Long.class),
                        resultSet.getString("group_item_status"),
                        resultSet.getObject("request_id", Long.class),
                        resultSet.getString("request_key"),
                        resultSet.getString("request_status"),
                        resultSet.getInt("offer_count"),
                        resultSet.getObject("offer_id", Long.class),
                        resultSet.getString("offer_status")
                )
        );
    }

    private record PmTransitionRelation(
            Long groupId,
            String groupStatus,
            int groupItemCount,
            Long groupItemId,
            String groupItemStatus,
            Long requestId,
            String requestKey,
            String requestStatus,
            int offerCount,
            Long offerId,
            String offerStatus
    ) {
    }

    private void runSql(String path) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new FileSystemResource(path));
        populator.setSqlScriptEncoding(StandardCharsets.UTF_8.name());
        populator.setContinueOnError(false);
        populator.execute(dataSource);
    }
}
