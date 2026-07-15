package org.sopt.ssingserver.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.sopt.ssingserver.domain.auth.token.AccessTokenProvider;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferDecision;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.support.AbstractMessageChannel;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// WebSocket 재접속 자체는 이벤트 재생을 보장하지 않으므로, 재접속 직후 REST snapshot으로 화면을 복구하는 계약을 실제 연결로 검증한다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC",
        "ssing.auth.jwt.issuer=ssing-instructor-matching-recovery-test",
        "ssing.auth.jwt.secret=integration-test-secret-key-for-hs256-signature",
        "ssing.auth.kakao.app-id=1234",
        "ssing.realtime.websocket.allowed-origins=http://localhost",
        "ssing.matching.search-scheduler.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
@Execution(ExecutionMode.SAME_THREAD)
@Import(InstructorMatchingRecoveryIntegrationTest.SubscriptionObserverConfig.class)
class InstructorMatchingRecoveryIntegrationTest {

    private static final Duration REALTIME_TIMEOUT = Duration.ofSeconds(5);
    private static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4.8"))
            .withDatabaseName("ssing_instructor_matching_recovery")
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

    @LocalServerPort
    private int serverPort;

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
    private SubscriptionObserver subscriptionObserver;

    @Autowired
    @Qualifier("brokerChannel")
    private AbstractMessageChannel brokerChannel;

    private final List<StompSession> sessions = new ArrayList<>();
    private WebSocketStompClient stompClient;
    private ThreadPoolTaskScheduler clientScheduler;

    @BeforeEach
    void resetDatabaseAndStartClient() {
        FLYWAY.clean();
        FLYWAY.migrate();
        runSql("db/seed/base/001_reference_data.sql");
        runSql("db/seed/base/010_dev_personas.sql");
        runSql("db/seed/scenarios/matching-price-vivaldi/seed.sql");
        runSql("db/seed/scenarios/matching-price-vivaldi/verify.sql");

        clientScheduler = new ThreadPoolTaskScheduler();
        clientScheduler.setPoolSize(1);
        clientScheduler.setThreadNamePrefix("instructor-matching-recovery-test-");
        clientScheduler.initialize();
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setTaskScheduler(clientScheduler);
        stompClient.start();
        subscriptionObserver.clear();
        brokerChannel.addInterceptor(subscriptionObserver);
    }

    @AfterEach
    void disconnectClients() {
        sessions.stream()
                .filter(StompSession::isConnected)
                .forEach(StompSession::disconnect);
        sessions.clear();
        if (stompClient != null) {
            stompClient.stop();
        }
        if (clientScheduler != null) {
            clientScheduler.shutdown();
        }
        brokerChannel.removeInterceptor(subscriptionObserver);
    }

    @Test
    void WebSocket이끊긴강사는_재접속후홈과상세REST로_PAYMENT_PENDING_협상을복구한다() throws Exception {
        Long consumerMemberId = personaMemberId("consumer-default");
        Long instructorMemberId = personaMemberId("instructor-approved-default");
        String consumerToken = accessTokenProvider.createAccessToken(consumerMemberId, MemberRole.CONSUMER);
        String instructorToken = accessTokenProvider.createAccessToken(instructorMemberId, MemberRole.INSTRUCTOR);

        EventSubscription initialSubscription = subscribe(instructorToken, "/user/queue/matching");
        long matchingRequestId = createMatchingRequest(consumerToken, """
                {
                  "resort": "VIVALDI_PARK",
                  "sport": "SKI",
                  "lessonLevel": "FIRST_TIME",
                  "requestedDurationMinutes": [120],
                  "participants": [
                    {"age": 10, "gender": "MALE"},
                    {"age": 12, "gender": "FEMALE"}
                  ],
                  "equipmentReady": true
                }
                """);
        JsonNode matchingEvent = awaitEvent(initialSubscription.events(), "MATCHING_OFFER_RECEIVED");
        long offerId = matchingEvent.path("offerId").asLong();
        long groupId = matchingEvent.path("groupId").asLong();
        assertThat(matchingEvent.path("recipientRole").asText()).isEqualTo("INSTRUCTOR");
        assertThat(matchingEvent.toString()).doesNotContain("\"participants\"");

        initialSubscription.session().disconnect();
        acceptInstructorOffer(instructorToken, offerId);
        acceptConsumerConfirmation(consumerToken, matchingRequestId);

        EventSubscription recoveredSubscription = subscribe(instructorToken, "/user/queue/matching");
        assertThat(recoveredSubscription.session().isConnected()).isTrue();

        mockMvc.perform(get("/api/v1/instructor/home")
                        .header(HttpHeaders.AUTHORIZATION, bearer(instructorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lessonCards[0].offerId").value(offerId))
                .andExpect(jsonPath("$.data.lessonCards[0].lessonId").doesNotExist())
                .andExpect(jsonPath("$.data.lessonCards[0].displayStatus").value("PAYMENT_PENDING"));

        mockMvc.perform(get("/api/v1/instructor/matching-offers/{offerId}", offerId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(instructorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recoveryState").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.offerId").value(offerId))
                .andExpect(jsonPath("$.data.groupId").value(groupId))
                .andExpect(jsonPath("$.data.offerStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.groupStatus").value("PAYMENT_PENDING"))
                .andExpect(jsonPath("$.data.matchingStatus").value("PAYMENT_PENDING"))
                .andExpect(jsonPath("$.data.requestSummary.requesterName").isNotEmpty())
                .andExpect(jsonPath("$.data.lessonSummary.startType").value("IMMEDIATE"))
                .andExpect(jsonPath("$.data.priceSummary.totalPaymentAmount").value(105_000))
                .andExpect(jsonPath("$.data.participants.length()").value(2))
                .andExpect(jsonPath("$.data.participants[0].age").value(10))
                .andExpect(jsonPath("$.data.participants[0].gender").value("MALE"))
                .andExpect(jsonPath("$.data.participants[1].age").value(12))
                .andExpect(jsonPath("$.data.participants[1].gender").value("FEMALE"))
                .andExpect(jsonPath("$.data.participants[0].participantId").doesNotExist())
                .andExpect(jsonPath("$.data.expiresAt").doesNotExist());
    }

    @Test
    void WebSocket이끊긴강사는_강사수락직후_WAITING_FOR_CONFIRMATION을복구하고_확정후홈으로전환한다()
            throws Exception {
        Long consumerMemberId = personaMemberId("consumer-default");
        Long instructorMemberId = personaMemberId("instructor-approved-default");
        String consumerToken = accessTokenProvider.createAccessToken(consumerMemberId, MemberRole.CONSUMER);
        String instructorToken = accessTokenProvider.createAccessToken(instructorMemberId, MemberRole.INSTRUCTOR);

        EventSubscription initialSubscription = subscribe(instructorToken, "/user/queue/matching");
        long matchingRequestId = createMatchingRequest(consumerToken);
        JsonNode matchingEvent = awaitEvent(initialSubscription.events(), "MATCHING_OFFER_RECEIVED");
        long offerId = matchingEvent.path("offerId").asLong();
        long groupId = matchingEvent.path("groupId").asLong();

        initialSubscription.session().disconnect();
        acceptInstructorOffer(instructorToken, offerId);

        EventSubscription recoveredSubscription = subscribe(instructorToken, "/user/queue/matching");
        assertThat(recoveredSubscription.session().isConnected()).isTrue();

        mockMvc.perform(get("/api/v1/instructor/home")
                        .header(HttpHeaders.AUTHORIZATION, bearer(instructorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lessonCards[0].offerId").value(offerId))
                .andExpect(jsonPath("$.data.lessonCards[0].lessonId").doesNotExist())
                .andExpect(jsonPath("$.data.lessonCards[0].displayStatus")
                        .value("WAITING_FOR_CONFIRMATION"));

        mockMvc.perform(get("/api/v1/instructor/matching-offers/{offerId}", offerId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(instructorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recoveryState").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.offerId").value(offerId))
                .andExpect(jsonPath("$.data.groupId").value(groupId))
                .andExpect(jsonPath("$.data.offerStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.groupStatus").value("INSTRUCTOR_ACCEPTED"))
                .andExpect(jsonPath("$.data.matchingStatus").value("WAITING_FOR_CONFIRMATION"));

        acceptConsumerConfirmation(consumerToken, matchingRequestId);
        long lessonId = completeMatchingPayment(consumerToken, matchingRequestId);

        mockMvc.perform(get("/api/v1/instructor/matching-offers/{offerId}", offerId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(instructorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.recoveryState").value("STALE"))
                .andExpect(jsonPath("$.data.offerId").value(offerId))
                .andExpect(jsonPath("$.data.groupId").doesNotExist())
                .andExpect(jsonPath("$.data.matchingStatus").doesNotExist())
                .andExpect(jsonPath("$.data.participants").doesNotExist());

        MvcResult homeResult = mockMvc.perform(get("/api/v1/instructor/home")
                        .header(HttpHeaders.AUTHORIZATION, bearer(instructorToken)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode confirmedLessonCard = findLessonCardByOfferId(
                objectMapper.readTree(homeResult.getResponse().getContentAsByteArray()),
                offerId
        );
        assertThat(confirmedLessonCard.path("offerId").asLong()).isEqualTo(offerId);
        assertThat(confirmedLessonCard.path("lessonId").asLong()).isEqualTo(lessonId);
        assertThat(confirmedLessonCard.path("displayStatus").asText()).isEqualTo("CONFIRMED");
    }

    @Test
    void 다른강사와_없는제안은_404이고_본인종료제안은_STALE을_반환한다() throws Exception {
        Long consumerMemberId = personaMemberId("consumer-default");
        Long instructorMemberId = personaMemberId("instructor-approved-default");
        String consumerToken = accessTokenProvider.createAccessToken(consumerMemberId, MemberRole.CONSUMER);
        String instructorToken = accessTokenProvider.createAccessToken(instructorMemberId, MemberRole.INSTRUCTOR);

        EventSubscription subscription = subscribe(instructorToken, "/user/queue/matching");
        createMatchingRequest(consumerToken);
        long offerId = awaitEvent(subscription.events(), "MATCHING_OFFER_RECEIVED")
                .path("offerId")
                .asLong();

        Long otherInstructorMemberId = createApprovedInstructorMember(
                "복구검증다른강사",
                "010-0000-0001"
        );
        String otherInstructorToken = accessTokenProvider.createAccessToken(
                otherInstructorMemberId,
                MemberRole.INSTRUCTOR
        );

        assertOfferDetailNotFound(otherInstructorToken, offerId);
        assertOfferDetailNotFound(instructorToken, Long.MAX_VALUE);

        respondInstructorOffer(instructorToken, offerId, MatchingOfferDecision.REJECTED);

        assertOfferDetailNotFound(otherInstructorToken, offerId);

        mockMvc.perform(get("/api/v1/instructor/matching-offers/{offerId}", offerId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(instructorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recoveryState").value("STALE"))
                .andExpect(jsonPath("$.data.offerId").value(offerId))
                .andExpect(jsonPath("$.data.offerStatus").doesNotExist());
    }

    private EventSubscription subscribe(String token, String destination) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add(HttpHeaders.AUTHORIZATION, bearer(token));
        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        handshakeHeaders.setOrigin("http://localhost");
        StompSession session = stompClient.connectAsync(
                        "ws://localhost:{port}/ws/realtime",
                        handshakeHeaders,
                        connectHeaders,
                        new StompSessionHandlerAdapter() {
                        },
                        serverPort
                )
                .get(REALTIME_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        sessions.add(session);

        BlockingQueue<JsonNode> events = new LinkedBlockingQueue<>();
        session.subscribe(destination, new JsonFrameHandler(events));
        subscriptionObserver.await(destination.substring("/user".length()));
        return new EventSubscription(session, events);
    }

    private JsonNode awaitEvent(BlockingQueue<JsonNode> events, String eventType) throws Exception {
        long deadlineNanos = System.nanoTime() + REALTIME_TIMEOUT.toNanos();
        while (System.nanoTime() < deadlineNanos) {
            long remainingNanos = deadlineNanos - System.nanoTime();
            JsonNode event = events.poll(remainingNanos, TimeUnit.NANOSECONDS);
            if (event == null) {
                break;
            }
            if (eventType.equals(event.path("eventType").asText())) {
                return event;
            }
        }
        throw new AssertionError("Expected realtime event was not received: " + eventType);
    }

    private long createMatchingRequest(String consumerToken) throws Exception {
        return createMatchingRequest(
                consumerToken,
                new FileSystemResource("db/seed/scenarios/matching-price-vivaldi/request.json")
                        .getContentAsString(StandardCharsets.UTF_8)
        );
    }

    private long createMatchingRequest(String consumerToken, String requestBody) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/consumer/matching-requests")
                        .header(HttpHeaders.AUTHORIZATION, bearer(consumerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray())
                .at("/data/matchingRequestId")
                .asLong();
    }

    private void acceptInstructorOffer(String instructorToken, long offerId) throws Exception {
        respondInstructorOffer(instructorToken, offerId, MatchingOfferDecision.ACCEPTED);
    }

    private void respondInstructorOffer(
            String instructorToken,
            long offerId,
            MatchingOfferDecision decision
    ) throws Exception {
        mockMvc.perform(patch("/api/v1/instructor/matching-offers/{offerId}", offerId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(instructorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"%s"}
                                """.formatted(decision.name())))
                .andExpect(status().isOk());
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
                .andExpect(status().isOk());
    }

    private long completeMatchingPayment(String consumerToken, long matchingRequestId) throws Exception {
        MvcResult result = mockMvc.perform(post(
                        "/api/v1/consumer/matching-requests/{matchingRequestId}/payment",
                        matchingRequestId
                )
                        .header(HttpHeaders.AUTHORIZATION, bearer(consumerToken)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray())
                .at("/data/lessonId")
                .asLong();
    }

    private Long createApprovedInstructorMember(String nickname, String phone) {
        long memberId = insertAndGetId(
                """
                INSERT INTO members (nickname, role, status, created_at, updated_at)
                VALUES (?, 'INSTRUCTOR', 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """,
                nickname
        );
        Long sourceInstructorMemberId = personaMemberId("instructor-approved-default");
        int insertedProfiles = jdbcTemplate.update(
                """
                INSERT INTO instructor_profiles (
                    member_id,
                    resort_id,
                    real_name,
                    phone,
                    gender,
                    birth_date,
                    intro,
                    career_start_date,
                    level,
                    certificate_type,
                    experience,
                    approval_status,
                    approved_at,
                    created_at,
                    updated_at
                )
                SELECT ?,
                       resort_id,
                       ?,
                       ?,
                       gender,
                       birth_date,
                       intro,
                       career_start_date,
                       level,
                       certificate_type,
                       experience,
                       'APPROVED',
                       UTC_TIMESTAMP(6),
                       UTC_TIMESTAMP(6),
                       UTC_TIMESTAMP(6)
                FROM instructor_profiles
                WHERE member_id = ?
                """,
                memberId,
                nickname,
                phone,
                sourceInstructorMemberId
        );
        assertThat(insertedProfiles).isEqualTo(1);
        return memberId;
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

    private void assertOfferDetailNotFound(String instructorToken, long offerId) throws Exception {
        mockMvc.perform(get("/api/v1/instructor/matching-offers/{offerId}", offerId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(instructorToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("MATCHING_OFFER_NOT_FOUND"))
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    private JsonNode findLessonCardByOfferId(JsonNode homeResponse, long offerId) {
        for (JsonNode lessonCard : homeResponse.at("/data/lessonCards")) {
            if (lessonCard.has("offerId") && lessonCard.path("offerId").asLong() == offerId) {
                return lessonCard;
            }
        }
        throw new AssertionError("Instructor home did not contain lesson card for offer: " + offerId);
    }

    private Long personaMemberId(String personaKey) {
        return jdbcTemplate.queryForObject(
                "SELECT member_id FROM dev_personas WHERE persona_key = ?",
                Long.class,
                personaKey
        );
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private void runSql(String path) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new FileSystemResource(path));
        populator.setSqlScriptEncoding(StandardCharsets.UTF_8.name());
        populator.setContinueOnError(false);
        populator.execute(dataSource);
    }

    private record EventSubscription(
            StompSession session,
            BlockingQueue<JsonNode> events
    ) {
    }

    private final class JsonFrameHandler implements StompFrameHandler {

        private final BlockingQueue<JsonNode> events;

        private JsonFrameHandler(BlockingQueue<JsonNode> events) {
            this.events = events;
        }

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return byte[].class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            try {
                events.add(objectMapper.readTree((byte[]) payload));
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to decode STOMP event payload", exception);
            }
        }
    }

    @TestConfiguration
    static class SubscriptionObserverConfig {

        @Bean
        SubscriptionObserver subscriptionObserver() {
            return new SubscriptionObserver();
        }
    }

    static class SubscriptionObserver implements ExecutorChannelInterceptor {

        private final BlockingQueue<String> destinations = new LinkedBlockingQueue<>();

        @Override
        public void afterMessageHandled(
                Message<?> message,
                MessageChannel channel,
                MessageHandler handler,
                Exception exception
        ) {
            String destination = SimpMessageHeaderAccessor.getDestination(message.getHeaders());
            if (exception == null
                    && handler instanceof SimpleBrokerMessageHandler
                    && SimpMessageType.SUBSCRIBE.equals(
                            SimpMessageHeaderAccessor.getMessageType(message.getHeaders())
                    )) {
                destinations.add(destination);
            }
        }

        void await(String expectedDestinationPrefix) throws Exception {
            long deadlineNanos = System.nanoTime() + REALTIME_TIMEOUT.toNanos();
            while (System.nanoTime() < deadlineNanos) {
                long remainingNanos = deadlineNanos - System.nanoTime();
                String destination = destinations.poll(remainingNanos, TimeUnit.NANOSECONDS);
                if (destination != null && destination.startsWith(expectedDestinationPrefix)) {
                    return;
                }
            }
            throw new AssertionError("Server did not register STOMP subscription: " + expectedDestinationPrefix);
        }

        void clear() {
            destinations.clear();
        }
    }
}
