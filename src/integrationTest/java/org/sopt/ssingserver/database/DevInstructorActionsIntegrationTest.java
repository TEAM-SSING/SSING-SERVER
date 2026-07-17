package org.sopt.ssingserver.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import org.sopt.ssingserver.database.support.BaseSeedLoader;
import org.sopt.ssingserver.database.support.DatabaseCleaner;
import org.sopt.ssingserver.database.support.SharedMySqlDatabase;
import org.sopt.ssingserver.domain.instructor.dev.dto.request.DevInstructorConfigurationRequest;
import org.sopt.ssingserver.domain.instructor.dev.dto.request.ExecuteDevInstructorActionRequest;
import org.sopt.ssingserver.domain.instructor.dev.enums.DevInstructorActionKey;
import org.sopt.ssingserver.domain.instructor.dev.error.DevInstructorErrorCode;
import org.sopt.ssingserver.domain.instructor.dev.service.DevInstructorActionTransactionService;
import org.sopt.ssingserver.domain.instructor.dto.request.InstructorMatchingExposureRequest;
import org.sopt.ssingserver.domain.instructor.dto.response.InstructorMatchingExposureResponse;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.domain.instructor.service.InstructorService;
import org.sopt.ssingserver.domain.matching.dto.command.MatchingCreationCommand;
import org.sopt.ssingserver.domain.matching.dto.command.MatchingParticipantCommand;
import org.sopt.ssingserver.domain.matching.service.MatchingEventDispatcher;
import org.sopt.ssingserver.domain.matching.service.MatchingOrchestrationService;
import org.sopt.ssingserver.domain.member.enums.Gender;
import org.sopt.ssingserver.global.error.BusinessException;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.sopt.ssingserver.global.error.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC",
        "ssing.dev-instructor-actions.enabled=true",
        "ssing.matching.search-scheduler.enabled=false",
        "ssing.auth.jwt.issuer=ssing-dev-instructor-actions-test",
        "ssing.auth.jwt.secret=integration-test-secret-key-for-hs256-signature",
        "ssing.auth.kakao.app-id=1234"
})
@AutoConfigureMockMvc
@ActiveProfiles({"integration-test", "local"})
@Execution(ExecutionMode.SAME_THREAD)
class DevInstructorActionsIntegrationTest {

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
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DevInstructorActionTransactionService devInstructorActionTransactionService;

    @Autowired
    private InstructorService instructorService;

    @Autowired
    private MatchingOrchestrationService matchingOrchestrationService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private MatchingEventDispatcher matchingEventDispatcher;

    @BeforeEach
    void resetDatabase() {
        dropRollbackConstraint();
        DatabaseCleaner.clean(dataSource);
        BaseSeedLoader.apply(dataSource);
    }

    @Test
    void 실제_Kakao회원은_신청_승인과설정_시작_중단을_단계별로_실행한다() throws Exception {
        long memberId = createKakaoMember("실제카카오강사", "kakao-real-instructor-1");

        JsonNode initialList = getMembers();
        assertThat(initialList.at("/data/totalElements").asLong()).isEqualTo(1);
        assertThat(initialList.at("/data/members/0/memberId").asLong()).isEqualTo(memberId);
        assertThat(initialList.at("/data/members/0/oauthAccountId").asLong()).isPositive();
        assertThat(initialList.at("/data/members/0").has("providerUserId")).isFalse();
        assertThat(actionKeys(initialList.at("/data/members/0")))
                .containsExactly("CREATE_APPLICATION");
        assertThat(jdbcTemplate.queryForObject("select count(*) from dev_personas", Integer.class))
                .isGreaterThan(0);

        JsonNode applicationCreated = executeAction(
                memberId,
                "CREATE_APPLICATION",
                initialList.at("/data/members/0/stateToken").asText(),
                null
        );
        JsonNode pending = applicationCreated.at("/data/after");
        assertThat(pending.path("memberRole").asText()).isEqualTo("CONSUMER");
        assertThat(pending.path("instructorApprovalStatus").asText()).isEqualTo("PENDING");
        assertThat(pending.path("instructorProfileId").asLong()).isPositive();
        assertThat(actionKeys(pending)).containsExactly("APPROVE_WITH_CONFIGURATION");
        assertThat(jdbcTemplate.queryForObject(
                "select approved_at is null from instructor_profiles where member_id = ?",
                Boolean.class,
                memberId
        )).isTrue();

        JsonNode approvedResult = executeAction(
                memberId,
                "APPROVE_WITH_CONFIGURATION",
                pending.path("stateToken").asText(),
                configuration(100_000, 20_000)
        );
        JsonNode approved = approvedResult.at("/data/after");
        assertThat(approved.path("memberRole").asText()).isEqualTo("INSTRUCTOR");
        assertThat(approved.path("instructorApprovalStatus").asText()).isEqualTo("APPROVED");
        assertThat(approved.at("/configuration/resortCode").asText()).isEqualTo("VIVALDI_PARK");
        assertThat(approved.at("/configuration/sport").asText()).isEqualTo("SKI");
        assertThat(approved.at("/configuration/complete").asBoolean()).isTrue();
        assertThat(approved.at("/configuration/exposed").asBoolean()).isFalse();
        assertThat(approved.at("/configuration/activePricePolicyIds").size()).isEqualTo(1);
        assertThat(actionKeys(approved)).containsExactly("SAVE_CONFIGURATION", "START_MATCHING");
        assertApprovedGraph(memberId, false, 100_000, 20_000);

        JsonNode staleRetry = executeActionConflict(
                memberId,
                "APPROVE_WITH_CONFIGURATION",
                pending.path("stateToken").asText(),
                configuration(150_000, 30_000)
        );
        assertThat(staleRetry.path("code").asText()).isEqualTo("DEV_INSTRUCTOR_STATE_CHANGED");
        assertApprovedGraph(memberId, false, 100_000, 20_000);

        JsonNode startedResult = executeAction(
                memberId,
                "START_MATCHING",
                approved.path("stateToken").asText(),
                null
        );
        JsonNode started = startedResult.at("/data/after");
        assertThat(started.at("/configuration/exposed").asBoolean()).isTrue();
        assertThat(actionKeys(started)).containsExactly("STOP_MATCHING");
        assertApprovedGraph(memberId, true, 100_000, 20_000);

        JsonNode stoppedResult = executeAction(
                memberId,
                "STOP_MATCHING",
                started.path("stateToken").asText(),
                null
        );
        JsonNode stopped = stoppedResult.at("/data/after");
        assertThat(stopped.at("/configuration/exposed").asBoolean()).isFalse();
        assertThat(actionKeys(stopped)).containsExactly("SAVE_CONFIGURATION", "START_MATCHING");
        assertApprovedGraph(memberId, false, 100_000, 20_000);
    }

    @Test
    void UI를_우회한_잘못된_가격은_승인전체를_거절하고_PENDING을_유지한다() throws Exception {
        long memberId = createKakaoMember("가격검증강사", "kakao-price-validation");
        JsonNode initial = getMembers().at("/data/members/0");
        JsonNode pending = executeAction(
                memberId,
                "CREATE_APPLICATION",
                initial.path("stateToken").asText(),
                null
        ).at("/data/after");

        JsonNode validationFailure = executeActionBadRequest(
                memberId,
                "APPROVE_WITH_CONFIGURATION",
                pending.path("stateToken").asText(),
                configuration(102_000, 20_000)
        );

        assertThat(validationFailure.path("code").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(validationFailure.at("/errors/basePriceAmount").asText()).contains("5000원 단위");
        assertPendingGraph(memberId);
    }

    @Test
    void 진행중인_소비자_매칭이_있으면_역할변경_승인을_보여주지도_실행하지도_않는다() throws Exception {
        long memberId = createKakaoMember("진행중소비자", "kakao-active-consumer-flow");
        JsonNode initial = getMembers().at("/data/members/0");
        executeAction(
                memberId,
                "CREATE_APPLICATION",
                initial.path("stateToken").asText(),
                null
        );
        insertActiveMatchingRequest(memberId);

        JsonNode blocked = getMembers().at("/data/members/0");
        assertThat(actionKeys(blocked)).isEmpty();
        assertThat(blocked.path("diagnostics").toString())
                .contains("진행 중인 소비자 매칭 또는 확정 강습");

        JsonNode conflict = executeActionConflict(
                memberId,
                "APPROVE_WITH_CONFIGURATION",
                blocked.path("stateToken").asText(),
                configuration(100_000, 20_000)
        );
        assertThat(conflict.path("code").asText()).isEqualTo("DEV_INSTRUCTOR_ACTION_NOT_AVAILABLE");
        assertPendingGraph(memberId);
    }

    @Test
    void 설정저장과_노출시작이_겹치면_노출응답도_커밋된_최신가격을_사용한다() throws Exception {
        long memberId = createKakaoMember("동시설정강사", "kakao-concurrent-configuration");
        JsonNode initial = getMembers().at("/data/members/0");
        JsonNode pending = executeAction(
                memberId,
                "CREATE_APPLICATION",
                initial.path("stateToken").asText(),
                null
        ).at("/data/after");
        JsonNode approved = executeAction(
                memberId,
                "APPROVE_WITH_CONFIGURATION",
                pending.path("stateToken").asText(),
                configuration(100_000, 20_000)
        ).at("/data/after");

        DevInstructorConfigurationRequest changedConfiguration = new DevInstructorConfigurationRequest(
                "VIVALDI_PARK",
                Sport.SKI,
                List.of(LessonLevel.FIRST_TIME, LessonLevel.BEGINNER),
                List.of(120, 180),
                3,
                150_000,
                30_000
        );
        ExecuteDevInstructorActionRequest saveRequest = new ExecuteDevInstructorActionRequest(
                DevInstructorActionKey.SAVE_CONFIGURATION,
                approved.path("stateToken").asText(),
                changedConfiguration
        );
        InstructorMatchingExposureRequest exposureRequest = new InstructorMatchingExposureRequest(
                Sport.SKI,
                List.of(LessonLevel.FIRST_TIME, LessonLevel.BEGINNER),
                List.of(120, 180),
                3,
                true
        );
        CountDownLatch configurationSaved = new CountDownLatch(1);
        CountDownLatch allowConfigurationCommit = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> configurationSave = executor.submit(() -> {
                TransactionTemplate transaction = new TransactionTemplate(transactionManager);
                transaction.executeWithoutResult(status -> {
                    devInstructorActionTransactionService.execute(memberId, saveRequest);
                    configurationSaved.countDown();
                    await(allowConfigurationCommit, "Dev configuration commit timed out.");
                });
            });

            assertThat(configurationSaved.await(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            Future<InstructorMatchingExposureResponse> exposureStart = executor.submit(() ->
                    instructorService.startExposure(memberId, exposureRequest)
            );

            assertThatThrownBy(() -> exposureStart.get(
                    LOCK_BLOCK_ASSERTION_MILLIS,
                    TimeUnit.MILLISECONDS
            )).isInstanceOf(TimeoutException.class);

            allowConfigurationCommit.countDown();
            configurationSave.get(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            InstructorMatchingExposureResponse response = exposureStart.get(
                    CONCURRENCY_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            );

            assertThat(response.pricePolicy().basePriceAmount()).isEqualTo(150_000);
            assertThat(response.pricePolicy().additionalPersonPriceAmount()).isEqualTo(30_000);
            assertThat(response.estimatedLessonPriceAmount()).isEqualTo(206_500);
            assertApprovedGraph(memberId, true, 150_000, 30_000);
        } finally {
            allowConfigurationCommit.countDown();
            executor.shutdownNow();
            executor.awaitTermination(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Test
    void 강사승인이_먼저_회원잠금을_잡으면_대기하던_소비자매칭생성은_거절된다() throws Exception {
        long memberId = createKakaoMember("승인선행회원", "kakao-approval-first");
        JsonNode initial = getMembers().at("/data/members/0");
        JsonNode pending = executeAction(
                memberId,
                "CREATE_APPLICATION",
                initial.path("stateToken").asText(),
                null
        ).at("/data/after");
        ExecuteDevInstructorActionRequest approvalRequest = new ExecuteDevInstructorActionRequest(
                DevInstructorActionKey.APPROVE_WITH_CONFIGURATION,
                pending.path("stateToken").asText(),
                devConfiguration(100_000, 20_000)
        );
        MatchingCreationCommand matchingCommand = matchingCommand(memberId);
        CountDownLatch approvalMutated = new CountDownLatch(1);
        CountDownLatch allowApprovalCommit = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> approval = executor.submit(() -> {
                TransactionTemplate transaction = new TransactionTemplate(transactionManager);
                transaction.executeWithoutResult(status -> {
                    devInstructorActionTransactionService.execute(memberId, approvalRequest);
                    approvalMutated.countDown();
                    await(allowApprovalCommit, "Instructor approval commit timed out.");
                });
            });

            assertThat(approvalMutated.await(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            Future<ErrorCode> matchingCreation = executor.submit(() ->
                    matchingCreationError(matchingCommand)
            );
            assertThatThrownBy(() -> matchingCreation.get(
                    LOCK_BLOCK_ASSERTION_MILLIS,
                    TimeUnit.MILLISECONDS
            )).isInstanceOf(TimeoutException.class);

            allowApprovalCommit.countDown();
            approval.get(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertThat(matchingCreation.get(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    .isSameAs(CommonErrorCode.FORBIDDEN);
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from matching_requests where member_id = ?",
                    Integer.class,
                    memberId
            )).isZero();
            assertApprovedGraph(memberId, false, 100_000, 20_000);
        } finally {
            allowApprovalCommit.countDown();
            executor.shutdownNow();
            executor.awaitTermination(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Test
    void 소비자매칭생성이_먼저_회원잠금을_잡으면_대기하던_강사승인은_거절된다() throws Exception {
        long memberId = createKakaoMember("매칭선행회원", "kakao-matching-first");
        JsonNode initial = getMembers().at("/data/members/0");
        JsonNode pending = executeAction(
                memberId,
                "CREATE_APPLICATION",
                initial.path("stateToken").asText(),
                null
        ).at("/data/after");
        ExecuteDevInstructorActionRequest approvalRequest = new ExecuteDevInstructorActionRequest(
                DevInstructorActionKey.APPROVE_WITH_CONFIGURATION,
                pending.path("stateToken").asText(),
                devConfiguration(100_000, 20_000)
        );
        MatchingCreationCommand matchingCommand = matchingCommand(memberId);
        CountDownLatch matchingCreated = new CountDownLatch(1);
        CountDownLatch allowMatchingCommit = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> matchingCreation = executor.submit(() -> {
                TransactionTemplate transaction = new TransactionTemplate(transactionManager);
                transaction.executeWithoutResult(status -> {
                    matchingOrchestrationService.createImmediateMatchingRequest(matchingCommand);
                    matchingCreated.countDown();
                    await(allowMatchingCommit, "Consumer matching commit timed out.");
                });
            });

            assertThat(matchingCreated.await(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            Future<ErrorCode> approval = executor.submit(() ->
                    instructorActionError(memberId, approvalRequest)
            );
            assertThatThrownBy(() -> approval.get(
                    LOCK_BLOCK_ASSERTION_MILLIS,
                    TimeUnit.MILLISECONDS
            )).isInstanceOf(TimeoutException.class);

            allowMatchingCommit.countDown();
            matchingCreation.get(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertThat(approval.get(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    .isIn(
                            DevInstructorErrorCode.DEV_INSTRUCTOR_STATE_CHANGED,
                            DevInstructorErrorCode.DEV_INSTRUCTOR_ACTION_NOT_AVAILABLE
                    );
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from matching_requests where member_id = ?",
                    Integer.class,
                    memberId
            )).isEqualTo(1);
            assertPendingGraph(memberId);
        } finally {
            allowMatchingCommit.countDown();
            executor.shutdownNow();
            executor.awaitTermination(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Test
    void 승인_저장중_DB오류가_나면_500으로_드러내고_앞선_변경도_함께_되돌린다() throws Exception {
        long memberId = createKakaoMember("롤백검증강사", "kakao-rollback-verification");
        JsonNode initial = getMembers().at("/data/members/0");
        JsonNode pending = executeAction(
                memberId,
                "CREATE_APPLICATION",
                initial.path("stateToken").asText(),
                null
        ).at("/data/after");

        long instructorProfileId = jdbcTemplate.queryForObject(
                "select id from instructor_profiles where member_id = ?",
                Long.class,
                memberId
        );
        jdbcTemplate.execute("""
                ALTER TABLE instructor_price_policies
                ADD CONSTRAINT chk_reject_dev_instructor_price
                CHECK (instructor_profile_id <> %d)
                """.formatted(instructorProfileId));
        try {
            JsonNode failure = executeActionInternalError(
                    memberId,
                    "APPROVE_WITH_CONFIGURATION",
                    pending.path("stateToken").asText(),
                    configuration(100_000, 20_000)
            );
            assertThat(failure.path("code").asText()).isEqualTo("INTERNAL_ERROR");
        } finally {
            dropRollbackConstraint();
        }

        assertPendingGraph(memberId);
    }

    @Test
    void dev_persona처럼_Kakao_OAuth연결이_없는_회원은_목록과_동작대상이_아니다() throws Exception {
        JsonNode list = getMembers();
        assertThat(list.at("/data/totalElements").asLong()).isZero();
        long personaMemberId = jdbcTemplate.queryForObject(
                "select member_id from dev_personas order by id limit 1",
                Long.class
        );

        MvcResult result = mockMvc.perform(post(
                        "/dev/auth/kakao-members/{memberId}/instructor-actions",
                        personaMemberId
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "actionKey", "CREATE_APPLICATION",
                                "stateToken", "v1:fake"
                        ))))
                .andExpect(status().isNotFound())
                .andReturn();

        assertThat(objectMapper.readTree(result.getResponse().getContentAsByteArray())
                .path("code")
                .asText()).isEqualTo("DEV_INSTRUCTOR_MEMBER_NOT_FOUND");
    }

    private long createKakaoMember(String nickname, String providerUserId) {
        jdbcTemplate.update("""
                insert into members (
                    created_at,
                    updated_at,
                    nickname,
                    profile_image_url,
                    role,
                    status
                ) values (UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), ?, null, 'CONSUMER', 'ACTIVE')
                """, nickname);
        long memberId = jdbcTemplate.queryForObject(
                "select id from members where nickname = ? order by id desc limit 1",
                Long.class,
                nickname
        );
        jdbcTemplate.update("""
                insert into oauth_accounts (
                    created_at,
                    updated_at,
                    member_id,
                    provider_user_id,
                    provider
                ) values (UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), ?, ?, 'KAKAO')
                """, memberId, providerUserId);
        return memberId;
    }

    private void insertActiveMatchingRequest(long memberId) {
        long resortId = jdbcTemplate.queryForObject(
                "select id from resorts where code = 'VIVALDI_PARK'",
                Long.class
        );
        jdbcTemplate.update("""
                insert into matching_requests (
                    headcount,
                    is_equipment_ready,
                    canceled_at,
                    created_at,
                    expires_at,
                    matching_offer_id,
                    member_id,
                    resort_id,
                    updated_at,
                    lesson_level,
                    sport,
                    status,
                    status_reason
                ) values (1, true, null, UTC_TIMESTAMP(6), null, null, ?, ?, UTC_TIMESTAMP(6),
                          'FIRST_TIME', 'SKI', 'REQUESTED', null)
                """, memberId, resortId);
    }

    private JsonNode getMembers() throws Exception {
        MvcResult result = mockMvc.perform(get("/dev/auth/kakao-members?page=0&size=100"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private JsonNode executeAction(
            long memberId,
            String actionKey,
            String stateToken,
            Map<String, Object> configuration
    ) throws Exception {
        MvcResult result = executeActionRaw(memberId, actionKey, stateToken, configuration)
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private JsonNode executeActionConflict(
            long memberId,
            String actionKey,
            String stateToken,
            Map<String, Object> configuration
    ) throws Exception {
        MvcResult result = executeActionRaw(memberId, actionKey, stateToken, configuration)
                .andExpect(status().isConflict())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private JsonNode executeActionBadRequest(
            long memberId,
            String actionKey,
            String stateToken,
            Map<String, Object> configuration
    ) throws Exception {
        MvcResult result = executeActionRaw(memberId, actionKey, stateToken, configuration)
                .andExpect(status().isBadRequest())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private JsonNode executeActionInternalError(
            long memberId,
            String actionKey,
            String stateToken,
            Map<String, Object> configuration
    ) throws Exception {
        MvcResult result = executeActionRaw(memberId, actionKey, stateToken, configuration)
                .andExpect(status().isInternalServerError())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private org.springframework.test.web.servlet.ResultActions executeActionRaw(
            long memberId,
            String actionKey,
            String stateToken,
            Map<String, Object> configuration
    ) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("actionKey", actionKey);
        body.put("stateToken", stateToken);
        if (configuration != null) {
            body.put("configuration", configuration);
        }
        return mockMvc.perform(post(
                        "/dev/auth/kakao-members/{memberId}/instructor-actions",
                        memberId
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body)));
    }

    private Map<String, Object> configuration(int basePrice, int additionalPrice) {
        Map<String, Object> configuration = new LinkedHashMap<>();
        configuration.put("resortCode", "VIVALDI_PARK");
        configuration.put("sport", "SKI");
        configuration.put("lessonLevels", List.of("FIRST_TIME", "BEGINNER"));
        configuration.put("availableDurationMinutes", List.of(120, 180));
        configuration.put("maxHeadcount", 3);
        configuration.put("basePriceAmount", basePrice);
        configuration.put("additionalPersonPriceAmount", additionalPrice);
        return configuration;
    }

    private DevInstructorConfigurationRequest devConfiguration(int basePrice, int additionalPrice) {
        return new DevInstructorConfigurationRequest(
                "VIVALDI_PARK",
                Sport.SKI,
                List.of(LessonLevel.FIRST_TIME, LessonLevel.BEGINNER),
                List.of(120, 180),
                3,
                basePrice,
                additionalPrice
        );
    }

    private MatchingCreationCommand matchingCommand(long memberId) {
        return MatchingCreationCommand.of(
                memberId,
                "VIVALDI_PARK",
                Sport.SKI,
                LessonLevel.FIRST_TIME,
                List.of(120),
                true,
                List.of(MatchingParticipantCommand.of("동시성 강습생", 25, Gender.MALE))
        );
    }

    private ErrorCode matchingCreationError(MatchingCreationCommand command) {
        try {
            matchingOrchestrationService.createImmediateMatchingRequest(command);
            return null;
        } catch (BusinessException exception) {
            return exception.getErrorCode();
        }
    }

    private ErrorCode instructorActionError(
            long memberId,
            ExecuteDevInstructorActionRequest request
    ) {
        try {
            devInstructorActionTransactionService.execute(memberId, request);
            return null;
        } catch (BusinessException exception) {
            return exception.getErrorCode();
        }
    }

    private List<String> actionKeys(JsonNode member) {
        return member.path("availableActions").valueStream().map(JsonNode::asText).toList();
    }

    private void assertApprovedGraph(
            long memberId,
            boolean exposed,
            int basePrice,
            int additionalPrice
    ) {
        assertThat(jdbcTemplate.queryForObject(
                "select role from members where id = ?",
                String.class,
                memberId
        )).isEqualTo("INSTRUCTOR");
        assertThat(jdbcTemplate.queryForObject(
                "select approval_status from instructor_profiles where member_id = ?",
                String.class,
                memberId
        )).isEqualTo("APPROVED");
        assertThat(jdbcTemplate.queryForObject(
                """
                select count(*)
                from instructor_matching_settings setting
                join instructor_profiles profile on profile.id = setting.instructor_profile_id
                where profile.member_id = ? and setting.is_exposed = ?
                """,
                Integer.class,
                memberId,
                exposed
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                """
                select count(*)
                from instructor_price_policies price
                join instructor_profiles profile on profile.id = price.instructor_profile_id
                where profile.member_id = ?
                  and price.is_active = true
                  and price.base_price_amount = ?
                  and price.additional_person_price_amount = ?
                """,
                Integer.class,
                memberId,
                basePrice,
                additionalPrice
        )).isEqualTo(1);
    }

    private void assertPendingGraph(long memberId) {
        assertThat(jdbcTemplate.queryForObject(
                "select role from members where id = ?",
                String.class,
                memberId
        )).isEqualTo("CONSUMER");
        assertThat(jdbcTemplate.queryForObject(
                "select approval_status from instructor_profiles where member_id = ?",
                String.class,
                memberId
        )).isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject(
                "select resort_id from instructor_profiles where member_id = ?",
                Long.class,
                memberId
        )).isNull();
        assertThat(jdbcTemplate.queryForObject(
                """
                select count(*)
                from instructor_matching_settings setting
                join instructor_profiles profile on profile.id = setting.instructor_profile_id
                where profile.member_id = ?
                """,
                Integer.class,
                memberId
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                """
                select count(*)
                from instructor_price_policies price
                join instructor_profiles profile on profile.id = price.instructor_profile_id
                where profile.member_id = ?
                """,
                Integer.class,
                memberId
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                """
                select count(*)
                from instructor_profile_certificates certificate
                join instructor_profiles profile on profile.id = certificate.instructor_profile_id
                where profile.member_id = ?
                """,
                Integer.class,
                memberId
        )).isZero();
    }

    private void dropRollbackConstraint() {
        Integer constraintCount = jdbcTemplate.queryForObject(
                """
                select count(*)
                from information_schema.table_constraints
                where constraint_schema = database()
                  and table_name = 'instructor_price_policies'
                  and constraint_name = 'chk_reject_dev_instructor_price'
                """,
                Integer.class
        );
        if (constraintCount != null && constraintCount > 0) {
            jdbcTemplate.execute("""
                    ALTER TABLE instructor_price_policies
                    DROP CHECK chk_reject_dev_instructor_price
                    """);
        }
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
}
