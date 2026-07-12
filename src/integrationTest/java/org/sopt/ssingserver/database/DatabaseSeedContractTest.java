package org.sopt.ssingserver.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.testcontainers.containers.MySQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC",
        "spring.task.scheduling.enabled=false",
        "ssing.matching.search-scheduler.enabled=true",
        "ssing.auth.jwt.issuer=ssing-integration-test",
        "ssing.auth.jwt.secret=integration-test-secret-key-for-hs256-signature",
        "ssing.auth.kakao.app-id=1234"
})
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class DatabaseSeedContractTest {

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.8")
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
    private MatchingSearchScheduler matchingSearchScheduler;

    // 외부 실시간 전달은 별도 후속 검증으로 분리하고, 요청 생성·탐색·가격 저장은 실제 빈으로 검증한다.
    @MockitoBean
    private MatchingEventDispatcher matchingEventDispatcher;

    @BeforeEach
    void resetDatabaseAndApplySeed() {
        FLYWAY.clean();
        FLYWAY.migrate();
        runSql("db/seed/base/001_reference_data.sql");
        runSql("db/seed/base/010_dev_personas.sql");
        runSql("db/seed/scenarios/matching-price-vivaldi/seed.sql");
        runSql("db/seed/scenarios/matching-price-vivaldi/verify.sql");
    }

    @Test
    void 독립_검증마다_깨끗한_MySQL에서_migration과_seed_SQL_계약을_확인한다() {
        assertMandatoryAndSeedInvariants();
    }

    @Test
    void 소비자_API_생성과_강사_API_readback_후_scheduler를_실행해도_85000원_상태가_유지된다() throws Exception {
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
        JsonNode offerBeforeScheduler = readInstructorOffer(instructorToken);
        assertConsumerReadback(consumerToken, matchingRequestId);
        assertPriceReadback(offerBeforeScheduler);

        matchingSearchScheduler.runScheduledSearch();

        JsonNode offerAfterScheduler = readInstructorOffer(instructorToken);
        assertConsumerReadback(consumerToken, matchingRequestId);
        assertPriceReadback(offerAfterScheduler);
        assertThat(offerAfterScheduler.at("/data/items/0/offerId").asLong())
                .isEqualTo(offerBeforeScheduler.at("/data/items/0/offerId").asLong());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM matching_offers", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM matching_offer_price_snapshots",
                Integer.class
        )).isEqualTo(1);
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

    private JsonNode responseJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private String scenarioRequestJson() throws Exception {
        return new FileSystemResource("db/seed/scenarios/matching-price-vivaldi/request.json")
                .getContentAsString(StandardCharsets.UTF_8);
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
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1",
                Integer.class
        )).isEqualTo(2);
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

    private void runSql(String path) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new FileSystemResource(path));
        populator.setContinueOnError(false);
        populator.execute(dataSource);
    }
}
