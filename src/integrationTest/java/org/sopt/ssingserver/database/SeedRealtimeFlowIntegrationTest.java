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
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
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
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC",
        "spring.task.scheduling.enabled=false",
        "ssing.matching.search-scheduler.enabled=true",
        "ssing.auth.jwt.issuer=ssing-realtime-integration-test",
        "ssing.auth.jwt.secret=integration-test-secret-key-for-hs256-signature",
        "ssing.auth.kakao.app-id=1234",
        "ssing.realtime.websocket.allowed-origins=http://localhost"
})
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
@Execution(ExecutionMode.SAME_THREAD)
@Import(SeedRealtimeFlowIntegrationTest.SubscriptionObserverConfig.class)
class SeedRealtimeFlowIntegrationTest {

    private static final Duration REALTIME_TIMEOUT = Duration.ofSeconds(5);
    private static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4.8"))
            .withDatabaseName("ssing_seed_realtime")
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
        clientScheduler.setThreadNamePrefix("seed-realtime-test-");
        clientScheduler.initialize();
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setTaskScheduler(clientScheduler);
        stompClient.start();
    }

    @AfterEach
    void disconnectClients() {
        sessions.stream()
                .filter(StompSession::isConnected)
                .forEach(StompSession::disconnect);
        sessions.clear();
        stompClient.stop();
        clientScheduler.shutdown();
    }

    // 실제 네트워크 구독부터 REST 상태 변경, 개인 큐 이벤트, 최종 DB 조회까지 한 흐름으로 증명한다.
    @Test
    void seed_사용자들이_실제_STOMP로_매칭과_강습_이벤트를_받고_REST_DB_상태도_일치한다() throws Exception {
        Long consumerMemberId = personaMemberId("consumer-default");
        Long instructorMemberId = personaMemberId("instructor-approved-default");
        String consumerToken = accessTokenProvider.createAccessToken(consumerMemberId, MemberRole.CONSUMER);
        String instructorToken = accessTokenProvider.createAccessToken(instructorMemberId, MemberRole.INSTRUCTOR);

        EventSubscription instructorMatching = subscribe(instructorToken, "/user/queue/matching");

        long matchingRequestId = createMatchingRequest(consumerToken);
        JsonNode matchingEvent = awaitEvent(instructorMatching.events(), "MATCHING_OFFER_RECEIVED");
        long offerId = matchingEvent.path("offerId").asLong();
        long groupId = matchingEvent.path("groupId").asLong();

        assertThat(matchingEvent.path("recipientRole").asText()).isEqualTo("INSTRUCTOR");
        assertThat(offerId).isPositive();
        assertThat(groupId).isPositive();
        assertThat(matchingEvent.has("matchingRequestId")).isFalse();
        assertConsumerMatchingReadback(consumerToken, matchingRequestId, groupId);
        assertOfferReadback(instructorToken, offerId, groupId);

        acceptInstructorOffer(instructorToken, offerId);
        acceptConsumerConfirmation(consumerToken, matchingRequestId);
        long lessonId = completeConsumerPayment(consumerToken, matchingRequestId);

        EventSubscription consumerLesson = subscribe(consumerToken, "/user/queue/lesson");
        EventSubscription instructorLesson = subscribe(instructorToken, "/user/queue/lesson");

        confirmLessonStart(consumerToken, lessonId, "CONFIRMED");
        awaitEvent(consumerLesson.events(), "LESSON_START_CONFIRMATION_UPDATED", "lessonId", lessonId);
        awaitEvent(instructorLesson.events(), "LESSON_START_CONFIRMATION_UPDATED", "lessonId", lessonId);

        confirmLessonStart(instructorToken, lessonId, "IN_PROGRESS");
        awaitEvent(consumerLesson.events(), "LESSON_STARTED", "lessonId", lessonId);
        awaitEvent(instructorLesson.events(), "LESSON_STARTED", "lessonId", lessonId);

        completeLesson(consumerToken, lessonId);
        JsonNode consumerCompleted = awaitEvent(
                consumerLesson.events(),
                "LESSON_COMPLETED",
                "lessonId",
                lessonId
        );
        JsonNode instructorCompleted = awaitEvent(
                instructorLesson.events(),
                "LESSON_COMPLETED",
                "lessonId",
                lessonId
        );

        assertThat(consumerCompleted.path("recipientRole").asText()).isEqualTo("CONSUMER");
        assertThat(instructorCompleted.path("recipientRole").asText()).isEqualTo("INSTRUCTOR");
        assertThat(consumerCompleted.path("lessonStatus").asText()).isEqualTo("COMPLETED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM lessons WHERE id = ?",
                String.class,
                lessonId
        )).isEqualTo("COMPLETED");
        mockMvc.perform(get("/api/v1/consumer/lessons/{lessonId}", lessonId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lessonStatus").value("COMPLETED"));
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
        subscriptionObserver.await(destination);
        return new EventSubscription(events);
    }

    private JsonNode awaitEvent(
            BlockingQueue<JsonNode> events,
            String eventType,
            String resourceIdField,
            long resourceId
    ) throws Exception {
        long deadlineNanos = System.nanoTime() + REALTIME_TIMEOUT.toNanos();
        while (System.nanoTime() < deadlineNanos) {
            long remainingNanos = deadlineNanos - System.nanoTime();
            JsonNode event = events.poll(remainingNanos, TimeUnit.NANOSECONDS);
            if (event == null) {
                break;
            }
            if (eventType.equals(event.path("eventType").asText())
                    && resourceId == event.path(resourceIdField).asLong()) {
                return event;
            }
        }
        throw new AssertionError("Expected realtime event was not received: " + eventType + ", " + resourceId);
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
        MvcResult result = mockMvc.perform(post("/api/v1/consumer/matching-requests")
                        .header(HttpHeaders.AUTHORIZATION, bearer(consumerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new FileSystemResource("db/seed/scenarios/matching-price-vivaldi/request.json")
                                .getContentAsString(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated())
                .andReturn();
        return responseJson(result).at("/data/matchingRequestId").asLong();
    }

    private void assertConsumerMatchingReadback(
            String consumerToken,
            long matchingRequestId,
            long groupId
    ) throws Exception {
        mockMvc.perform(get("/api/v1/consumer/matching-requests/{matchingRequestId}", matchingRequestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(consumerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matchingRequestId").value(matchingRequestId))
                .andExpect(jsonPath("$.data.groupId").value(groupId))
                .andExpect(jsonPath("$.data.matchingStatus").value("WAITING_FOR_INSTRUCTOR"));
    }

    private void assertOfferReadback(String instructorToken, long offerId, long groupId) throws Exception {
        mockMvc.perform(get("/api/v1/instructor/matching-offers")
                        .header(HttpHeaders.AUTHORIZATION, bearer(instructorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].offerId").value(offerId))
                .andExpect(jsonPath("$.data.items[0].groupId").value(groupId))
                .andExpect(jsonPath("$.data.items[0].priceSummary.totalPaymentAmount").value(85_000));
    }

    private void acceptInstructorOffer(String instructorToken, long offerId) throws Exception {
        mockMvc.perform(patch("/api/v1/instructor/matching-offers/{offerId}", offerId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(instructorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"ACCEPTED"}
                                """))
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

    private long completeConsumerPayment(String consumerToken, long matchingRequestId) throws Exception {
        MvcResult result = mockMvc.perform(post(
                        "/api/v1/consumer/matching-requests/{matchingRequestId}/payment",
                        matchingRequestId
                )
                        .header(HttpHeaders.AUTHORIZATION, bearer(consumerToken)))
                .andExpect(status().isOk())
                .andReturn();
        return responseJson(result).at("/data/lessonId").asLong();
    }

    private void confirmLessonStart(String token, long lessonId, String lessonStatus) throws Exception {
        mockMvc.perform(post("/api/v1/lessons/{lessonId}/start-confirmation", lessonId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lessonStatus").value(lessonStatus));
    }

    private void completeLesson(String token, long lessonId) throws Exception {
        mockMvc.perform(post("/api/v1/lessons/{lessonId}/completion", lessonId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lessonStatus").value("COMPLETED"));
    }

    private JsonNode responseJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
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
        populator.setContinueOnError(false);
        populator.execute(dataSource);
    }

    private record EventSubscription(BlockingQueue<JsonNode> events) {
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
    static class SubscriptionObserverConfig implements WebSocketMessageBrokerConfigurer {

        private final SubscriptionObserver subscriptionObserver = new SubscriptionObserver();

        @Bean
        SubscriptionObserver subscriptionObserver() {
            return subscriptionObserver;
        }

        @Override
        public void configureClientInboundChannel(ChannelRegistration registration) {
            registration.interceptors(subscriptionObserver);
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
            StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
            if (exception == null
                    && handler instanceof SimpleBrokerMessageHandler
                    && StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                // Simple broker의 구독 등록 처리가 끝난 시점을 테스트 준비 완료 신호로 사용한다.
                destinations.add(accessor.getDestination());
            }
        }

        void await(String expectedDestination) throws Exception {
            long deadlineNanos = System.nanoTime() + REALTIME_TIMEOUT.toNanos();
            while (System.nanoTime() < deadlineNanos) {
                long remainingNanos = deadlineNanos - System.nanoTime();
                String destination = destinations.poll(remainingNanos, TimeUnit.NANOSECONDS);
                if (expectedDestination.equals(destination)) {
                    return;
                }
            }
            throw new AssertionError("Server did not accept STOMP subscription: " + expectedDestination);
        }
    }
}
