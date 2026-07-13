package org.sopt.ssingserver.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.sopt.ssingserver.domain.auth.token.AccessTokenProvider;
import org.sopt.ssingserver.domain.matching.config.MatchingSearchSchedulerConfig;
import org.sopt.ssingserver.domain.matching.service.MatchingEventDispatcher;
import org.sopt.ssingserver.domain.matching.service.MatchingSearchScheduler;
import org.sopt.ssingserver.domain.matching.service.MatchingSearchTriggerService;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.scheduling.config.TaskManagementConfigUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC",
        "ssing.auth.jwt.issuer=ssing-integration-test",
        "ssing.auth.jwt.secret=integration-test-secret-key-for-hs256-signature",
        "ssing.auth.kakao.app-id=1234"
})
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
@Execution(ExecutionMode.SAME_THREAD)
class DatabaseSeedContractTest {

    private static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4.8"))
            .withDatabaseName("ssing_seed_contract")
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
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccessTokenProvider accessTokenProvider;

    @Autowired
    private MatchingSearchTriggerService matchingSearchTriggerService;

    @Autowired
    private ApplicationContext applicationContext;

    // 외부 실시간 전달만 후속 검증으로 분리하고, 요청부터 결제·강습 저장까지는 실제 빈으로 검증한다.
    @MockitoBean
    private MatchingEventDispatcher matchingEventDispatcher;

    @BeforeEach
    void resetDatabaseAndApplyBaseSeed() {
        FLYWAY.clean();
        FLYWAY.migrate();
        runSql("db/seed/base/001_reference_data.sql");
        runSql("db/seed/base/010_dev_personas.sql");
    }

    @ParameterizedTest(name = "{0} seed 계약")
    @MethodSource("scenarioKeys")
    void 모든_scenario를_깨끗한_MySQL에서_독립적으로_검증한다(String scenarioKey) throws Exception {
        assertScenarioContractFiles(scenarioKey);
        applyScenario(scenarioKey);
    }

    @Test
    void integration_test에서는_시간에_따른_scheduler가_자동_실행되지_않는다() {
        assertThat(applicationContext.containsBean(
                TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME
        )).isFalse();
        assertThat(applicationContext.getBeansOfType(MatchingSearchScheduler.class)).isEmpty();
        assertThat(applicationContext.containsBean(
                MatchingSearchSchedulerConfig.MATCHING_SEARCH_TASK_SCHEDULER
        )).isFalse();
    }

    @Test
    void PM_snapshot은_원본_계약과_일치하고_명시적_재탐색에서만_GROUPED로_전이한다() throws Exception {
        applyScenario("pm-full-requested-catalog");

        PmSeedSnapshotContract.assertMatches(jdbcTemplate, objectMapper);
        assertPmCatalogTransitionCounts(16, 0, 0);

        matchingSearchTriggerService.triggerAllRequested();

        assertPmCatalogTransitionCounts(14, 2, 2);
        assertThat(groupedPmRequestKeys()).containsExactlyInAnyOrder(
                "pm-consumer-001:HIGH1:SKI:BEGINNER",
                "consumer-default:VIVALDI_PARK:SKI:FIRST_TIME"
        );
        List<String> firstTransitionRows = pmTransitionRows();

        matchingSearchTriggerService.triggerAllRequested();

        assertPmCatalogTransitionCounts(14, 2, 2);
        assertThat(pmTransitionRows()).containsExactlyElementsOf(firstTransitionRows);
    }

    @Test
    void 단건_매칭은_명시적_재탐색_후에도_85000원으로_결제되고_강습까지_확정된다() throws Exception {
        applyScenario("matching-price-vivaldi");
        assertMandatoryAndSeedInvariants();

        Long consumerMemberId = personaMemberId("consumer-default");
        Long instructorMemberId = personaMemberId("instructor-approved-default");
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
        JsonNode offerBeforeRetrigger = readInstructorOffer(instructorToken);
        assertConsumerReadback(consumerToken, matchingRequestId);
        assertPriceReadback(offerBeforeRetrigger);

        matchingSearchTriggerService.triggerAllRequested();

        JsonNode offerAfterRetrigger = readInstructorOffer(instructorToken);
        assertConsumerReadback(consumerToken, matchingRequestId);
        assertPriceReadback(offerAfterRetrigger);
        long offerId = offerAfterRetrigger.at("/data/items/0/offerId").asLong();
        assertThat(offerId).isEqualTo(offerBeforeRetrigger.at("/data/items/0/offerId").asLong());
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
        acceptConsumerConfirmation(consumerToken, matchingRequestId);
        long lessonId = completeConsumerPayment(consumerToken, matchingRequestId);

        assertConsumerConfirmedReadback(consumerToken, matchingRequestId, lessonId);
        assertSingleRequestPaymentAndLesson(matchingRequestId, offerId, lessonId);
    }

    @Test
    void 후보가_없는_요청은_명시적_재탐색_후에도_SEARCHING을_유지한다() throws Exception {
        applyScenario("matching-no-candidate-alpensia");
        String consumerToken = accessTokenProvider.createAccessToken(
                personaMemberId("consumer-default"),
                MemberRole.CONSUMER
        );

        long matchingRequestId = createSearchingRequest(
                consumerToken,
                "db/seed/scenarios/matching-no-candidate-alpensia/request.json"
        );

        matchingSearchTriggerService.triggerAllRequested();

        mockMvc.perform(get("/api/v1/consumer/matching-requests/{matchingRequestId}", matchingRequestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matchingStatus").value("SEARCHING"))
                .andExpect(jsonPath("$.data.requestStatus").value("REQUESTED"));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM matching_request_groups", Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM matching_offers", Integer.class))
                .isZero();
    }

    @Test
    void 한_소비자는_오크밸리_네_요청과_열여섯_참가자를_독립적으로_생성한다() throws Exception {
        applyScenario("matching-multi-request-oak");
        Long memberId = personaMemberId("pm-consumer-007");
        String consumerToken = accessTokenProvider.createAccessToken(memberId, MemberRole.CONSUMER);
        List<Long> matchingRequestIds = new ArrayList<>();

        for (String requestName : List.of("a", "b", "c", "d")) {
            matchingRequestIds.add(createSearchingRequest(
                    consumerToken,
                    "db/seed/scenarios/matching-multi-request-oak/request-007-" + requestName + ".json"
            ));
        }

        matchingSearchTriggerService.triggerAllRequested();

        for (Long matchingRequestId : matchingRequestIds) {
            mockMvc.perform(get("/api/v1/consumer/matching-requests/{matchingRequestId}", matchingRequestId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(consumerToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.matchingStatus").value("SEARCHING"))
                    .andExpect(jsonPath("$.data.requestStatus").value("REQUESTED"));
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
    }

    private JsonNode readInstructorOffer(String instructorToken) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/instructor/matching-offers")
                        .header(HttpHeaders.AUTHORIZATION, bearer(instructorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
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
                .andExpect(jsonPath("$.data.offerStatus").value("OFFERED"));
    }

    private void assertPriceReadback(JsonNode response) {
        JsonNode items = response.at("/data/items");
        assertThat(items.size()).isEqualTo(1);
        JsonNode offer = items.get(0);
        assertThat(offer.path("offerStatus").asText()).isEqualTo("OFFERED");
        assertThat(offer.at("/lessonSummary/resort/code").asText()).isEqualTo("VIVALDI_PARK");
        assertThat(offer.at("/priceSummary/lessonPriceAmount").asInt()).isEqualTo(60_000);
        assertThat(offer.at("/priceSummary/resortPassFeeAmount").asInt()).isEqualTo(25_000);
        assertThat(offer.at("/priceSummary/totalPaymentAmount").asInt()).isEqualTo(85_000);
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
                WHERE persona.persona_key = 'instructor-approved-default'
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

    private List<String> groupedPmRequestKeys() {
        return jdbcTemplate.queryForList(
                """
                SELECT CONCAT(
                    persona.persona_key, ':', resort.code, ':', request.sport, ':', request.lesson_level
                )
                FROM matching_requests request
                JOIN dev_personas persona ON persona.member_id = request.member_id
                JOIN resorts resort ON resort.id = request.resort_id
                WHERE request.status = 'GROUPED'
                ORDER BY request.id
                """,
                String.class
        );
    }

    private List<String> pmTransitionRows() {
        return jdbcTemplate.queryForList(
                """
                SELECT CONCAT(
                    group_table.id, ':', group_item.matching_request_id, ':', offer.id, ':', offer.status
                )
                FROM matching_request_groups group_table
                JOIN matching_request_group_items group_item
                  ON group_item.matching_request_group_id = group_table.id
                JOIN matching_offers offer
                  ON offer.matching_request_group_id = group_table.id
                ORDER BY group_table.id, group_item.matching_request_id, offer.id
                """,
                String.class
        );
    }

    private void runSql(String path) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new FileSystemResource(path));
        populator.setContinueOnError(false);
        populator.execute(dataSource);
    }
}
