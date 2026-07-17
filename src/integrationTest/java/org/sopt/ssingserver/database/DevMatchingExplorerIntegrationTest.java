package org.sopt.ssingserver.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.sopt.ssingserver.database.support.BaseSeedLoader;
import org.sopt.ssingserver.database.support.DatabaseCleaner;
import org.sopt.ssingserver.database.support.SharedMySqlDatabase;
import org.sopt.ssingserver.domain.auth.token.AccessTokenProvider;
import org.sopt.ssingserver.domain.matching.service.MatchingEventDispatcher;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC",
        "ssing.matching.search-scheduler.enabled=false",
        "ssing.auth.jwt.issuer=ssing-dev-matching-explorer-test",
        "ssing.auth.jwt.secret=integration-test-secret-key-for-hs256-signature",
        "ssing.auth.kakao.app-id=1234"
})
@AutoConfigureMockMvc
@ActiveProfiles({"integration-test", "local"})
@Execution(ExecutionMode.SAME_THREAD)
class DevMatchingExplorerIntegrationTest {

    private static final String CONSUMER_PERSONA_KEY = "대뜸GOAT-성빈-일반강습생";
    private static final String INSTRUCTOR_PERSONA_KEY = "보법다른-유정-승인강사";

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

    @MockitoBean
    private MatchingEventDispatcher matchingEventDispatcher;

    @BeforeEach
    void resetDatabaseAndApplyMatchingSeed() {
        DatabaseCleaner.clean(dataSource);
        BaseSeedLoader.apply(dataSource);
        runSql("db/seed/scenarios/matching-price-vivaldi/seed.sql");
        runSql("db/seed/scenarios/matching-price-vivaldi/verify.sql");
    }

    @Test
    void dev_조회는_실제_관계와_가능동작을_상태별로_보여주고_DB를_변경하지_않는다() throws Exception {
        Long consumerMemberId = personaMemberId(CONSUMER_PERSONA_KEY);
        Long instructorMemberId = personaMemberId(INSTRUCTOR_PERSONA_KEY);
        String consumerToken = accessTokenProvider.createAccessToken(consumerMemberId, MemberRole.CONSUMER);
        String instructorToken = accessTokenProvider.createAccessToken(instructorMemberId, MemberRole.INSTRUCTOR);

        long matchingRequestId = createMatchingRequest(
                consumerToken,
                "db/seed/scenarios/matching-price-vivaldi/request.json"
        );
        jdbcTemplate.update(
                "update matching_request_participants set name = ? where matching_request_id = ?",
                "테스트 참가자",
                matchingRequestId
        );
        long offerId = jdbcTemplate.queryForObject(
                "select id from matching_offers order by id desc limit 1",
                Long.class
        );
        long groupId = jdbcTemplate.queryForObject(
                "select matching_request_group_id from matching_offers where id = ?",
                Long.class,
                offerId
        );
        long groupItemId = jdbcTemplate.queryForObject(
                "select id from matching_request_group_items where matching_request_id = ?",
                Long.class,
                matchingRequestId
        );

        Map<String, List<Map<String, Object>>> beforeListRead = matchingGraphSnapshot();
        JsonNode list = getJson("/dev/matching/requests?page=0&size=20");
        assertThat(matchingGraphSnapshot()).isEqualTo(beforeListRead);
        assertThat(list.at("/data/requests").size()).isEqualTo(1);
        assertThat(list.at("/data/requests/0/matchingRequestId").asLong()).isEqualTo(matchingRequestId);
        assertThat(list.at("/data/requests/0/matchingStatus").asText()).isEqualTo("WAITING_FOR_INSTRUCTOR");
        assertThat(list.at("/data/requests/0/groupId").asLong()).isEqualTo(groupId);
        assertThat(list.at("/data/requests/0/offerId").asLong()).isEqualTo(offerId);

        Map<String, List<Map<String, Object>>> beforeRead = matchingGraphSnapshot();
        JsonNode waitingForInstructor = getDetail(matchingRequestId);
        JsonNode repeatedRead = getDetail(matchingRequestId);

        assertResolved(waitingForInstructor, matchingRequestId, "WAITING_FOR_INSTRUCTOR");
        assertThat(waitingForInstructor.at("/data/participants/0/name").asText()).isEqualTo("테스트 참가자");
        assertThat(waitingForInstructor.at("/data/stateToken").asText()).isNotBlank();
        assertThat(repeatedRead.at("/data/stateToken").asText())
                .isEqualTo(waitingForInstructor.at("/data/stateToken").asText());
        assertThat(actionKeys(waitingForInstructor))
                .containsExactlyInAnyOrder("INSTRUCTOR_ACCEPT", "INSTRUCTOR_REJECT");
        assertThat(personaKeys(waitingForInstructor))
                .contains(CONSUMER_PERSONA_KEY, INSTRUCTOR_PERSONA_KEY);
        assertThat(resourceIds(waitingForInstructor, "MATCHING_REQUEST")).contains(matchingRequestId);
        assertThat(resourceIds(waitingForInstructor, "MATCHING_REQUEST_GROUP")).contains(groupId);
        assertThat(resourceIds(waitingForInstructor, "MATCHING_OFFER")).contains(offerId);
        assertThat(waitingForInstructor.at("/data/requestRelations/0/matchingRequestId").asLong())
                .isEqualTo(matchingRequestId);
        assertThat(waitingForInstructor.at("/data/requestRelations/0/consumerMemberId").asLong())
                .isEqualTo(consumerMemberId);
        assertThat(waitingForInstructor.at("/data/requestRelations/0/groupId").asLong()).isEqualTo(groupId);
        assertThat(waitingForInstructor.at("/data/requestRelations/0/offerId").asLong()).isEqualTo(offerId);
        assertThat(waitingForInstructor.at("/data/requestRelations/0/paymentId").isNull()).isTrue();
        JsonNode instructorAccept = findAction(waitingForInstructor, "INSTRUCTOR_ACCEPT");
        assertThat(instructorAccept.path("actor").path("memberId").asLong()).isEqualTo(instructorMemberId);
        assertThat(affectedMemberIds(instructorAccept)).containsExactlyInAnyOrder(
                instructorMemberId,
                consumerMemberId
        );
        assertThat(actionResourceChangeKeys(instructorAccept)).containsExactlyInAnyOrder(
                "MATCHING_OFFER#" + offerId + ".status:OFFERED->ACCEPTED",
                "MATCHING_REQUEST_GROUP#" + groupId + ".status:EXPOSED->INSTRUCTOR_ACCEPTED",
                "MATCHING_REQUEST#" + matchingRequestId + ".status:GROUPED->MATCHED",
                "MATCHING_REQUEST_GROUP_ITEM#" + groupItemId + ".status:NOT_REQUESTED->PENDING"
        );
        assertThat(matchingGraphSnapshot()).isEqualTo(beforeRead);

        acceptInstructorOffer(instructorToken, offerId);
        Map<String, List<Map<String, Object>>> beforeConfirmationRead = matchingGraphSnapshot();
        JsonNode waitingForConfirmation = getDetail(matchingRequestId);
        assertResolved(waitingForConfirmation, matchingRequestId, "WAITING_FOR_CONFIRMATION");
        assertThat(actionKeys(waitingForConfirmation))
                .containsExactlyInAnyOrder("CONSUMER_ACCEPT", "CONSUMER_REJECT");
        assertThat(waitingForConfirmation.at("/data/availableActions/0/actor/memberId").asLong())
                .isEqualTo(consumerMemberId);
        JsonNode consumerAccept = findAction(waitingForConfirmation, "CONSUMER_ACCEPT");
        assertThat(affectedMemberIds(consumerAccept)).containsExactly(consumerMemberId);
        assertThat(actionResourceChangeKeys(consumerAccept)).containsExactlyInAnyOrder(
                "MATCHING_REQUEST_GROUP_ITEM#" + groupItemId + ".status:PENDING->ACCEPTED",
                "MATCHING_REQUEST_GROUP#" + groupId + ".status:INSTRUCTOR_ACCEPTED->PAYMENT_PENDING",
                "MATCHING_REQUEST_PAYMENT#null.status:ABSENT->PENDING"
        );
        assertThat(matchingGraphSnapshot()).isEqualTo(beforeConfirmationRead);

        acceptConsumerConfirmation(consumerToken, matchingRequestId);
        Long paymentId = jdbcTemplate.queryForObject(
                "select id from matching_request_payments where matching_request_id = ?",
                Long.class,
                matchingRequestId
        );
        Map<String, List<Map<String, Object>>> beforePaymentRead = matchingGraphSnapshot();
        JsonNode paymentPending = getDetail(matchingRequestId);
        assertResolved(paymentPending, matchingRequestId, "PAYMENT_PENDING");
        assertThat(actionKeys(paymentPending)).containsExactly("PAYMENT_COMPLETE");
        assertThat(resourceIds(paymentPending, "MATCHING_REQUEST_PAYMENT")).containsExactly(paymentId);
        assertThat(paymentPending.at("/data/requestRelations/0/paymentId").asLong()).isEqualTo(paymentId);
        assertThat(paymentPending.at("/data/observedAt").asText()).isNotBlank();
        JsonNode paymentComplete = findAction(paymentPending, "PAYMENT_COMPLETE");
        assertThat(affectedMemberIds(paymentComplete)).containsExactlyInAnyOrder(
                instructorMemberId,
                consumerMemberId
        );
        assertThat(actionResourceChangeKeys(paymentComplete)).containsExactlyInAnyOrder(
                "MATCHING_REQUEST_PAYMENT#" + paymentId + ".status:PENDING->COMPLETED",
                "MATCHING_REQUEST_GROUP#" + groupId + ".status:PAYMENT_PENDING->CONFIRMED",
                "MATCHING_REQUEST#" + matchingRequestId + ".status:MATCHED->CONFIRMED",
                "LESSON#null.status:ABSENT->CONFIRMED"
        );
        assertThat(matchingGraphSnapshot()).isEqualTo(beforePaymentRead);
    }

    @Test
    void 결제완료_뒤_CONFIRMED와_강습진행중_이력은_정상관계로_조회한다() throws Exception {
        Long consumerMemberId = personaMemberId(CONSUMER_PERSONA_KEY);
        Long instructorMemberId = personaMemberId(INSTRUCTOR_PERSONA_KEY);
        String consumerToken = accessTokenProvider.createAccessToken(consumerMemberId, MemberRole.CONSUMER);
        String instructorToken = accessTokenProvider.createAccessToken(instructorMemberId, MemberRole.INSTRUCTOR);
        long matchingRequestId = createMatchingRequest(
                consumerToken,
                "db/seed/scenarios/matching-price-vivaldi/request.json"
        );
        long offerId = jdbcTemplate.queryForObject(
                "select id from matching_offers order by id desc limit 1",
                Long.class
        );
        acceptInstructorOffer(instructorToken, offerId);
        acceptConsumerConfirmation(consumerToken, matchingRequestId);
        long lessonId = completeConsumerPayment(consumerToken, matchingRequestId);

        JsonNode confirmed = getDetail(matchingRequestId);
        assertResolved(confirmed, matchingRequestId, "CONFIRMED");
        assertThat(findResource(confirmed, "LESSON", lessonId).path("status").asText()).isEqualTo("CONFIRMED");

        confirmLessonStart(consumerToken, lessonId);
        confirmLessonStart(instructorToken, lessonId);
        Map<String, List<Map<String, Object>>> beforeRead = matchingGraphSnapshot();
        JsonNode inProgress = getDetail(matchingRequestId);

        assertResolved(inProgress, matchingRequestId, "CONFIRMED");
        assertThat(findResource(inProgress, "LESSON", lessonId).path("status").asText())
                .isEqualTo("IN_PROGRESS");
        assertThat(matchingGraphSnapshot()).isEqualTo(beforeRead);
    }

    @Test
    void 후보가_없는_REQUESTED_요청은_SEARCHING이고_가능동작이_없다() throws Exception {
        Long consumerMemberId = personaMemberId(CONSUMER_PERSONA_KEY);
        String consumerToken = accessTokenProvider.createAccessToken(consumerMemberId, MemberRole.CONSUMER);
        long matchingRequestId = createMatchingRequest(
                consumerToken,
                "db/seed/scenarios/matching-no-candidate-alpensia/request.json"
        );

        Map<String, List<Map<String, Object>>> beforeRead = matchingGraphSnapshot();
        JsonNode searching = getDetail(matchingRequestId);

        assertResolved(searching, matchingRequestId, "SEARCHING");
        assertThat(searching.at("/data/availableActions").isEmpty()).isTrue();
        assertThat(searching.at("/data/resources").size()).isEqualTo(1);
        assertThat(searching.at("/data/resources/0/resourceType").asText()).isEqualTo("MATCHING_REQUEST");
        assertThat(matchingGraphSnapshot()).isEqualTo(beforeRead);
    }

    @Test
    void 관계가_깨진_요청은_다른_목록을_500으로_만들지_않고_진단만_반환한다() throws Exception {
        Long validConsumerMemberId = personaMemberId(CONSUMER_PERSONA_KEY);
        String validConsumerToken = accessTokenProvider.createAccessToken(validConsumerMemberId, MemberRole.CONSUMER);
        long validRequestId = createMatchingRequest(
                validConsumerToken,
                "db/seed/scenarios/matching-price-vivaldi/request.json"
        );
        Long brokenConsumerMemberId = createConsumerPersona("consumer-broken", "관계깨짐소비자");
        String brokenConsumerToken = accessTokenProvider.createAccessToken(
                brokenConsumerMemberId,
                MemberRole.CONSUMER
        );
        long matchingRequestId = createMatchingRequest(
                brokenConsumerToken,
                "db/seed/scenarios/matching-no-candidate-alpensia/request.json"
        );
        jdbcTemplate.update(
                "update matching_requests set status = 'GROUPED' where id = ?",
                matchingRequestId
        );

        Map<String, List<Map<String, Object>>> beforeRead = matchingGraphSnapshot();
        JsonNode list = getJson("/dev/matching/requests?page=0&size=20");
        JsonNode inconsistent = getDetail(matchingRequestId);

        assertThat(list.at("/data/requests").size()).isEqualTo(2);
        assertThat(findListRequest(list, validRequestId).path("resolutionState").asText()).isEqualTo("RESOLVED");
        assertThat(findListRequest(list, validRequestId).path("matchingStatus").asText())
                .isEqualTo("WAITING_FOR_INSTRUCTOR");
        assertThat(findListRequest(list, matchingRequestId).path("resolutionState").asText())
                .isEqualTo("INCONSISTENT");
        assertThat(inconsistent.at("/data/resolutionState").asText()).isEqualTo("INCONSISTENT");
        assertThat(inconsistent.at("/data/matchingStatus").isNull()).isTrue();
        assertThat(inconsistent.at("/data/availableActions").isEmpty()).isTrue();
        assertThat(inconsistent.at("/data/diagnostics").toString())
                .contains("matchingRequestId=" + matchingRequestId)
                .contains("group/item");
        assertThat(matchingGraphSnapshot()).isEqualTo(beforeRead);
    }

    @Test
    void 한_요청이_두_활성그룹에_동시에_묶이면_두_groupId를_진단하고_다른_요청은_정상조회한다() throws Exception {
        Long duplicatedConsumerMemberId = personaMemberId(CONSUMER_PERSONA_KEY);
        String duplicatedConsumerToken = accessTokenProvider.createAccessToken(
                duplicatedConsumerMemberId,
                MemberRole.CONSUMER
        );
        long duplicatedRequestId = createMatchingRequest(
                duplicatedConsumerToken,
                "db/seed/scenarios/matching-price-vivaldi/request.json"
        );
        long firstActiveGroupId = jdbcTemplate.queryForObject(
                "select matching_request_group_id from matching_request_group_items where matching_request_id = ?",
                Long.class,
                duplicatedRequestId
        );

        jdbcTemplate.update("""
                insert into matching_request_groups (duration_minutes, created_at, updated_at, status)
                values (180, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'CANDIDATE')
                """);
        long secondActiveGroupId = jdbcTemplate.queryForObject(
                "select id from matching_request_groups order by id desc limit 1",
                Long.class
        );
        jdbcTemplate.update("""
                insert into matching_request_group_items (
                    matching_request_group_id,
                    matching_request_id,
                    status,
                    created_at,
                    updated_at
                ) values (?, ?, 'NOT_REQUESTED', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, secondActiveGroupId, duplicatedRequestId);

        Long normalConsumerMemberId = createConsumerPersona("consumer-normal", "정상조회소비자");
        String normalConsumerToken = accessTokenProvider.createAccessToken(normalConsumerMemberId, MemberRole.CONSUMER);
        long normalRequestId = createMatchingRequest(
                normalConsumerToken,
                "db/seed/scenarios/matching-no-candidate-alpensia/request.json"
        );

        Map<String, List<Map<String, Object>>> beforeRead = matchingGraphSnapshot();
        JsonNode list = getJson("/dev/matching/requests?page=0&size=20");
        JsonNode inconsistent = getDetail(duplicatedRequestId);

        assertThat(findListRequest(list, normalRequestId).path("resolutionState").asText()).isEqualTo("RESOLVED");
        assertThat(findListRequest(list, normalRequestId).path("matchingStatus").asText()).isEqualTo("SEARCHING");
        JsonNode duplicatedListItem = findListRequest(list, duplicatedRequestId);
        assertThat(duplicatedListItem.path("resolutionState").asText()).isEqualTo("INCONSISTENT");
        assertThat(duplicatedListItem.path("availableActionKeys").isEmpty()).isTrue();
        assertThat(inconsistent.at("/data/resolutionState").asText()).isEqualTo("INCONSISTENT");
        assertThat(inconsistent.at("/data/matchingStatus").isNull()).isTrue();
        assertThat(inconsistent.at("/data/availableActions").isEmpty()).isTrue();
        assertThat(inconsistent.at("/data/diagnostics").toString())
                .contains("groupId=" + firstActiveGroupId)
                .contains("groupId=" + secondActiveGroupId)
                .contains("활성 group");
        assertThat(matchingGraphSnapshot()).isEqualTo(beforeRead);
    }

    @Test
    void 더_최신인_종료그룹은_기존_활성그룹_선택을_가리지_않는다() throws Exception {
        Long consumerMemberId = personaMemberId(CONSUMER_PERSONA_KEY);
        String consumerToken = accessTokenProvider.createAccessToken(consumerMemberId, MemberRole.CONSUMER);
        long matchingRequestId = createMatchingRequest(
                consumerToken,
                "db/seed/scenarios/matching-price-vivaldi/request.json"
        );
        long activeGroupId = jdbcTemplate.queryForObject(
                "select matching_request_group_id from matching_request_group_items where matching_request_id = ?",
                Long.class,
                matchingRequestId
        );
        jdbcTemplate.update("""
                insert into matching_request_groups (duration_minutes, created_at, updated_at, status)
                values (180, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'CANCELED')
                """);
        long closedGroupId = jdbcTemplate.queryForObject(
                "select id from matching_request_groups order by id desc limit 1",
                Long.class
        );
        jdbcTemplate.update("""
                insert into matching_request_group_items (
                    matching_request_group_id,
                    matching_request_id,
                    status,
                    created_at,
                    updated_at
                ) values (?, ?, 'CANCELED', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, closedGroupId, matchingRequestId);

        JsonNode detail = getDetail(matchingRequestId);

        assertResolved(detail, matchingRequestId, "WAITING_FOR_INSTRUCTOR");
        assertThat(detail.at("/data/requestRelations/0/groupId").asLong()).isEqualTo(activeGroupId);
        assertThat(resourceIds(detail, "MATCHING_REQUEST_GROUP")).containsExactly(activeGroupId);
    }

    @Test
    void 가격snapshot이나_participant가_누락되면_목록과_상세에서_동작을_숨긴다() throws Exception {
        Long firstConsumerMemberId = personaMemberId(CONSUMER_PERSONA_KEY);
        String firstConsumerToken = accessTokenProvider.createAccessToken(firstConsumerMemberId, MemberRole.CONSUMER);
        long missingSnapshotRequestId = createMatchingRequest(
                firstConsumerToken,
                "db/seed/scenarios/matching-price-vivaldi/request.json"
        );
        long offerId = jdbcTemplate.queryForObject(
                "select matching_offer_id from matching_offer_price_snapshots order by id desc limit 1",
                Long.class
        );
        jdbcTemplate.update("delete from matching_offer_price_snapshots where matching_offer_id = ?", offerId);

        Long secondConsumerMemberId = createConsumerPersona("consumer-participant-broken", "참가자누락소비자");
        String secondConsumerToken = accessTokenProvider.createAccessToken(secondConsumerMemberId, MemberRole.CONSUMER);
        long missingParticipantRequestId = createMatchingRequest(
                secondConsumerToken,
                "db/seed/scenarios/matching-no-candidate-alpensia/request.json"
        );
        jdbcTemplate.update(
                "delete from matching_request_participants where matching_request_id = ?",
                missingParticipantRequestId
        );

        Map<String, List<Map<String, Object>>> beforeRead = matchingGraphSnapshot();
        JsonNode list = getJson("/dev/matching/requests?page=0&size=20");
        JsonNode snapshotDetail = getDetail(missingSnapshotRequestId);
        JsonNode participantDetail = getDetail(missingParticipantRequestId);

        assertThat(findListRequest(list, missingSnapshotRequestId).path("resolutionState").asText())
                .isEqualTo("INCONSISTENT");
        assertThat(findListRequest(list, missingParticipantRequestId).path("resolutionState").asText())
                .isEqualTo("INCONSISTENT");
        assertThat(snapshotDetail.at("/data/availableActions").isEmpty()).isTrue();
        assertThat(snapshotDetail.at("/data/diagnostics").toString()).contains("price snapshot");
        assertThat(participantDetail.at("/data/availableActions").isEmpty()).isTrue();
        assertThat(participantDetail.at("/data/diagnostics").toString())
                .contains("headcount")
                .contains("participantCount");
        assertThat(matchingGraphSnapshot()).isEqualTo(beforeRead);
    }

    @Test
    void 현재_page_밖_다른그룹에도_같은_강사_live제안이_있으면_관계오류다() throws Exception {
        Long firstConsumerMemberId = personaMemberId(CONSUMER_PERSONA_KEY);
        String firstConsumerToken = accessTokenProvider.createAccessToken(firstConsumerMemberId, MemberRole.CONSUMER);
        long firstRequestId = createMatchingRequest(
                firstConsumerToken,
                "db/seed/scenarios/matching-price-vivaldi/request.json"
        );
        long firstOfferId = jdbcTemplate.queryForObject(
                "select id from matching_offers order by id desc limit 1",
                Long.class
        );
        long instructorProfileId = jdbcTemplate.queryForObject(
                "select instructor_profile_id from matching_offers where id = ?",
                Long.class,
                firstOfferId
        );

        Long secondConsumerMemberId = createConsumerPersona("consumer-second-live", "두번째협상소비자");
        String secondConsumerToken = accessTokenProvider.createAccessToken(secondConsumerMemberId, MemberRole.CONSUMER);
        long secondRequestId = createMatchingRequest(
                secondConsumerToken,
                "db/seed/scenarios/matching-no-candidate-alpensia/request.json"
        );
        jdbcTemplate.update("""
                insert into matching_request_groups (duration_minutes, created_at, updated_at, status)
                values (180, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'EXPOSED')
                """);
        long secondGroupId = jdbcTemplate.queryForObject(
                "select id from matching_request_groups order by id desc limit 1",
                Long.class
        );
        jdbcTemplate.update("""
                insert into matching_request_group_items (
                    matching_request_group_id,
                    matching_request_id,
                    status,
                    created_at,
                    updated_at
                ) values (?, ?, 'NOT_REQUESTED', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, secondGroupId, secondRequestId);
        jdbcTemplate.update(
                "update matching_requests set status = 'GROUPED' where id = ?",
                secondRequestId
        );
        jdbcTemplate.update("""
                insert into matching_offers (
                    created_at,
                    updated_at,
                    exposed_at,
                    instructor_profile_id,
                    matching_request_group_id,
                    status
                ) values (UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), ?, ?, 'OFFERED')
                """, instructorProfileId, secondGroupId);
        long secondOfferId = jdbcTemplate.queryForObject(
                "select id from matching_offers order by id desc limit 1",
                Long.class
        );
        cloneOfferPriceSnapshot(firstOfferId, secondOfferId);

        Map<String, List<Map<String, Object>>> beforeRead = matchingGraphSnapshot();
        JsonNode secondRequestPage = getJson("/dev/matching/requests?page=0&size=1");
        JsonNode firstRequestOnlyPage = getJson("/dev/matching/requests?page=1&size=1");
        JsonNode detail = getDetail(firstRequestId);

        assertThat(secondRequestPage.at("/data/page").asInt()).isZero();
        assertThat(secondRequestPage.at("/data/size").asInt()).isEqualTo(1);
        assertThat(secondRequestPage.at("/data/totalElements").asLong()).isEqualTo(2);
        assertThat(secondRequestPage.at("/data/totalPages").asInt()).isEqualTo(2);
        assertThat(secondRequestPage.at("/data/hasNext").asBoolean()).isTrue();
        assertThat(secondRequestPage.at("/data/requests/0/matchingRequestId").asLong())
                .isEqualTo(secondRequestId);
        assertThat(firstRequestOnlyPage.at("/data/page").asInt()).isEqualTo(1);
        assertThat(firstRequestOnlyPage.at("/data/size").asInt()).isEqualTo(1);
        assertThat(firstRequestOnlyPage.at("/data/totalElements").asLong()).isEqualTo(2);
        assertThat(firstRequestOnlyPage.at("/data/totalPages").asInt()).isEqualTo(2);
        assertThat(firstRequestOnlyPage.at("/data/hasNext").asBoolean()).isFalse();
        assertThat(firstRequestOnlyPage.at("/data/requests/0/matchingRequestId").asLong()).isEqualTo(firstRequestId);
        assertThat(firstRequestOnlyPage.at("/data/requests/0/resolutionState").asText()).isEqualTo("INCONSISTENT");
        assertThat(firstRequestOnlyPage.at("/data/requests/0/availableActionKeys").isEmpty()).isTrue();
        assertThat(detail.at("/data/resolutionState").asText()).isEqualTo("INCONSISTENT");
        assertThat(detail.at("/data/availableActions").isEmpty()).isTrue();
        assertThat(detail.at("/data/diagnostics").toString())
                .contains("instructorProfileId=" + instructorProfileId)
                .contains("groupIds")
                .contains("offerIds");
        assertThat(matchingGraphSnapshot()).isEqualTo(beforeRead);
    }

    @Test
    void 같은_group에_live_offer가_둘이면_두_offerId를_진단하고_동작을_숨긴다() throws Exception {
        Long consumerMemberId = personaMemberId(CONSUMER_PERSONA_KEY);
        String consumerToken = accessTokenProvider.createAccessToken(consumerMemberId, MemberRole.CONSUMER);
        long matchingRequestId = createMatchingRequest(
                consumerToken,
                "db/seed/scenarios/matching-price-vivaldi/request.json"
        );
        long firstOfferId = jdbcTemplate.queryForObject(
                "select id from matching_offers order by id desc limit 1",
                Long.class
        );
        long groupId = jdbcTemplate.queryForObject(
                "select matching_request_group_id from matching_offers where id = ?",
                Long.class,
                firstOfferId
        );
        long firstInstructorProfileId = jdbcTemplate.queryForObject(
                "select instructor_profile_id from matching_offers where id = ?",
                Long.class,
                firstOfferId
        );
        long secondInstructorProfileId = createAdditionalInstructorProfile(firstInstructorProfileId);
        jdbcTemplate.update("""
                insert into matching_offers (
                    created_at,
                    updated_at,
                    exposed_at,
                    instructor_profile_id,
                    matching_request_group_id,
                    status
                ) values (UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), ?, ?, 'OFFERED')
                """, secondInstructorProfileId, groupId);
        long secondOfferId = jdbcTemplate.queryForObject(
                "select id from matching_offers order by id desc limit 1",
                Long.class
        );
        cloneOfferPriceSnapshot(firstOfferId, secondOfferId);

        Map<String, List<Map<String, Object>>> beforeRead = matchingGraphSnapshot();
        JsonNode detail = getDetail(matchingRequestId);

        assertThat(detail.at("/data/resolutionState").asText()).isEqualTo("INCONSISTENT");
        assertThat(detail.at("/data/matchingStatus").isNull()).isTrue();
        assertThat(detail.at("/data/availableActions").isEmpty()).isTrue();
        assertThat(detail.at("/data/diagnostics").toString())
                .contains("groupId=" + groupId)
                .contains("offerIds")
                .contains(String.valueOf(firstOfferId))
                .contains(String.valueOf(secondOfferId));
        assertThat(matchingGraphSnapshot()).isEqualTo(beforeRead);
    }

    @Test
    void PAYMENT_PENDING의_payment가_누락되면_group요청_구성불일치를_진단한다() throws Exception {
        Long consumerMemberId = personaMemberId(CONSUMER_PERSONA_KEY);
        Long instructorMemberId = personaMemberId(INSTRUCTOR_PERSONA_KEY);
        String consumerToken = accessTokenProvider.createAccessToken(consumerMemberId, MemberRole.CONSUMER);
        String instructorToken = accessTokenProvider.createAccessToken(instructorMemberId, MemberRole.INSTRUCTOR);
        long matchingRequestId = createMatchingRequest(
                consumerToken,
                "db/seed/scenarios/matching-price-vivaldi/request.json"
        );
        long offerId = jdbcTemplate.queryForObject(
                "select id from matching_offers order by id desc limit 1",
                Long.class
        );
        acceptInstructorOffer(instructorToken, offerId);
        acceptConsumerConfirmation(consumerToken, matchingRequestId);
        jdbcTemplate.update(
                "delete from matching_request_payments where matching_request_id = ?",
                matchingRequestId
        );

        Map<String, List<Map<String, Object>>> beforeRead = matchingGraphSnapshot();
        JsonNode list = getJson("/dev/matching/requests?page=0&size=20");
        JsonNode detail = getDetail(matchingRequestId);

        JsonNode paymentMissingListItem = findListRequest(list, matchingRequestId);
        assertThat(paymentMissingListItem.path("resolutionState").asText()).isEqualTo("INCONSISTENT");
        assertThat(paymentMissingListItem.path("availableActionKeys").isEmpty()).isTrue();
        assertThat(detail.at("/data/resolutionState").asText()).isEqualTo("INCONSISTENT");
        assertThat(detail.at("/data/matchingStatus").isNull()).isTrue();
        assertThat(detail.at("/data/availableActions").isEmpty()).isTrue();
        assertThat(detail.at("/data/diagnostics").toString())
                .contains("group item 요청과 payment 요청 구성이 일치하지 않습니다");
        assertThat(matchingGraphSnapshot()).isEqualTo(beforeRead);
    }

    @Test
    void 확정전_lesson은_관계오류지만_사용자_직접취소와_lesson없음은_정상이다() throws Exception {
        Long consumerMemberId = personaMemberId(CONSUMER_PERSONA_KEY);
        String consumerToken = accessTokenProvider.createAccessToken(consumerMemberId, MemberRole.CONSUMER);
        long matchingRequestId = createMatchingRequest(
                consumerToken,
                "db/seed/scenarios/matching-price-vivaldi/request.json"
        );
        long offerId = jdbcTemplate.queryForObject(
                "select id from matching_offers order by id desc limit 1",
                Long.class
        );
        insertPrematureLesson(matchingRequestId, offerId);

        JsonNode prematureLesson = getDetail(matchingRequestId);
        assertThat(prematureLesson.at("/data/resolutionState").asText()).isEqualTo("INCONSISTENT");
        assertThat(prematureLesson.at("/data/matchingStatus").isNull()).isTrue();
        assertThat(prematureLesson.at("/data/availableActions").isEmpty()).isTrue();
        assertThat(prematureLesson.at("/data/diagnostics").toString()).contains("확정 전 요청에 lesson");

        jdbcTemplate.update("delete from lessons where matching_offer_id = ?", offerId);
        cancelMatchingRequest(consumerToken, matchingRequestId);
        Map<String, List<Map<String, Object>>> beforeCanceledRead = matchingGraphSnapshot();
        JsonNode canceled = getDetail(matchingRequestId);

        assertResolved(canceled, matchingRequestId, "CANCELED");
        assertThat(canceled.at("/data/requestStatusReason").asText()).isEqualTo("CONSUMER_CANCELED");
        assertThat(resourceIds(canceled, "LESSON")).isEmpty();
        assertThat(matchingGraphSnapshot()).isEqualTo(beforeCanceledRead);
    }

    @Test
    void 결제row가_있어도_원본_실행조건이_깨지면_PAYMENT_PENDING으로_정상표시하지_않는다() throws Exception {
        Long consumerMemberId = personaMemberId(CONSUMER_PERSONA_KEY);
        Long instructorMemberId = personaMemberId(INSTRUCTOR_PERSONA_KEY);
        String consumerToken = accessTokenProvider.createAccessToken(consumerMemberId, MemberRole.CONSUMER);
        String instructorToken = accessTokenProvider.createAccessToken(instructorMemberId, MemberRole.INSTRUCTOR);
        long matchingRequestId = createMatchingRequest(
                consumerToken,
                "db/seed/scenarios/matching-price-vivaldi/request.json"
        );
        long offerId = jdbcTemplate.queryForObject(
                "select id from matching_offers order by id desc limit 1",
                Long.class
        );
        acceptInstructorOffer(instructorToken, offerId);
        acceptConsumerConfirmation(consumerToken, matchingRequestId);
        jdbcTemplate.update(
                "update matching_requests set status = 'GROUPED' where id = ?",
                matchingRequestId
        );
        jdbcTemplate.update(
                "update matching_request_group_items set status = 'NOT_REQUESTED' where matching_request_id = ?",
                matchingRequestId
        );

        Map<String, List<Map<String, Object>>> beforeRead = matchingGraphSnapshot();
        JsonNode inconsistent = getDetail(matchingRequestId);

        assertThat(inconsistent.at("/data/resolutionState").asText()).isEqualTo("INCONSISTENT");
        assertThat(inconsistent.at("/data/matchingStatus").isNull()).isTrue();
        assertThat(inconsistent.at("/data/availableActions").isEmpty()).isTrue();
        assertThat(inconsistent.at("/data/diagnostics").toString())
                .contains("계산된 PAYMENT_PENDING")
                .contains("원본 관계 상태");
        assertThat(matchingGraphSnapshot()).isEqualTo(beforeRead);
    }

    @Test
    void MATCHED_요청의_matchingOffer_연결이_끊기면_실행가능동작을_숨긴다() throws Exception {
        Long consumerMemberId = personaMemberId(CONSUMER_PERSONA_KEY);
        Long instructorMemberId = personaMemberId(INSTRUCTOR_PERSONA_KEY);
        String consumerToken = accessTokenProvider.createAccessToken(consumerMemberId, MemberRole.CONSUMER);
        String instructorToken = accessTokenProvider.createAccessToken(instructorMemberId, MemberRole.INSTRUCTOR);
        long matchingRequestId = createMatchingRequest(
                consumerToken,
                "db/seed/scenarios/matching-price-vivaldi/request.json"
        );
        long offerId = jdbcTemplate.queryForObject(
                "select id from matching_offers order by id desc limit 1",
                Long.class
        );
        acceptInstructorOffer(instructorToken, offerId);
        jdbcTemplate.update(
                "update matching_requests set matching_offer_id = null where id = ?",
                matchingRequestId
        );

        Map<String, List<Map<String, Object>>> beforeRead = matchingGraphSnapshot();
        JsonNode inconsistent = getDetail(matchingRequestId);

        assertThat(inconsistent.at("/data/resolutionState").asText()).isEqualTo("INCONSISTENT");
        assertThat(inconsistent.at("/data/matchingStatus").isNull()).isTrue();
        assertThat(inconsistent.at("/data/availableActions").isEmpty()).isTrue();
        assertThat(inconsistent.at("/data/diagnostics").toString())
                .contains("matchingOffer")
                .contains("ACCEPTED offer");
        assertThat(matchingGraphSnapshot()).isEqualTo(beforeRead);
    }

    @Test
    void 제안전_CANDIDATE_그룹만_WAITING_FOR_TEAM이고_EXPOSED_무제안_그룹은_관계오류다() throws Exception {
        Long consumerMemberId = personaMemberId(CONSUMER_PERSONA_KEY);
        String consumerToken = accessTokenProvider.createAccessToken(consumerMemberId, MemberRole.CONSUMER);
        long matchingRequestId = createMatchingRequest(
                consumerToken,
                "db/seed/scenarios/matching-no-candidate-alpensia/request.json"
        );
        jdbcTemplate.update("""
                insert into matching_request_groups (duration_minutes, created_at, updated_at, status)
                values (180, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'CANDIDATE')
                """);
        Long groupId = jdbcTemplate.queryForObject(
                "select id from matching_request_groups order by id desc limit 1",
                Long.class
        );
        jdbcTemplate.update("""
                insert into matching_request_group_items (
                    matching_request_group_id,
                    matching_request_id,
                    status,
                    created_at,
                    updated_at
                ) values (?, ?, 'NOT_REQUESTED', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, groupId, matchingRequestId);
        jdbcTemplate.update(
                "update matching_requests set status = 'GROUPED' where id = ?",
                matchingRequestId
        );

        Map<String, List<Map<String, Object>>> beforeCandidateRead = matchingGraphSnapshot();
        JsonNode waitingForTeam = getDetail(matchingRequestId);
        assertResolved(waitingForTeam, matchingRequestId, "WAITING_FOR_TEAM");
        assertThat(matchingGraphSnapshot()).isEqualTo(beforeCandidateRead);

        jdbcTemplate.update(
                "update matching_request_groups set status = 'EXPOSED' where id = ?",
                groupId
        );
        Map<String, List<Map<String, Object>>> beforeExposedRead = matchingGraphSnapshot();
        JsonNode inconsistent = getDetail(matchingRequestId);

        assertThat(inconsistent.at("/data/resolutionState").asText()).isEqualTo("INCONSISTENT");
        assertThat(inconsistent.at("/data/matchingStatus").isNull()).isTrue();
        assertThat(inconsistent.at("/data/availableActions").isEmpty()).isTrue();
        assertThat(inconsistent.at("/data/diagnostics").toString())
                .contains("계산된 WAITING_FOR_TEAM")
                .contains("원본 관계 상태");
        assertThat(matchingGraphSnapshot()).isEqualTo(beforeExposedRead);
    }

    @Test
    void 재매칭이나_취소요청에_이전_EXPOSED_그룹이_살아있으면_동작을_숨기고_관계오류로_표시한다() throws Exception {
        Long consumerMemberId = personaMemberId(CONSUMER_PERSONA_KEY);
        String consumerToken = accessTokenProvider.createAccessToken(consumerMemberId, MemberRole.CONSUMER);
        long matchingRequestId = createMatchingRequest(
                consumerToken,
                "db/seed/scenarios/matching-price-vivaldi/request.json"
        );

        jdbcTemplate.update(
                "update matching_requests set status = 'REQUESTED', status_reason = 'INSTRUCTOR_REJECTED' where id = ?",
                matchingRequestId
        );
        Map<String, List<Map<String, Object>>> beforeRematchingRead = matchingGraphSnapshot();
        JsonNode rematching = getDetail(matchingRequestId);

        assertThat(rematching.at("/data/resolutionState").asText()).isEqualTo("INCONSISTENT");
        assertThat(rematching.at("/data/matchingStatus").isNull()).isTrue();
        assertThat(rematching.at("/data/availableActions").isEmpty()).isTrue();
        assertThat(rematching.at("/data/diagnostics").toString())
                .contains("계산된 REMATCHING")
                .contains("원본 관계 상태");
        assertThat(matchingGraphSnapshot()).isEqualTo(beforeRematchingRead);

        jdbcTemplate.update(
                "update matching_requests set status = 'CANCELED', status_reason = 'CONSUMER_CANCELED' where id = ?",
                matchingRequestId
        );
        Map<String, List<Map<String, Object>>> beforeCanceledRead = matchingGraphSnapshot();
        JsonNode canceled = getDetail(matchingRequestId);

        assertThat(canceled.at("/data/resolutionState").asText()).isEqualTo("INCONSISTENT");
        assertThat(canceled.at("/data/matchingStatus").isNull()).isTrue();
        assertThat(canceled.at("/data/availableActions").isEmpty()).isTrue();
        assertThat(canceled.at("/data/diagnostics").toString())
                .contains("계산된 CANCELED")
                .contains("원본 관계 상태");
        assertThat(matchingGraphSnapshot()).isEqualTo(beforeCanceledRead);
    }

    @Test
    void 다중요청_그룹은_강습생별_관계와_강사수락_영향을_실제_ID로_연결한다() throws Exception {
        Long firstConsumerId = personaMemberId(CONSUMER_PERSONA_KEY);
        Long instructorId = personaMemberId(INSTRUCTOR_PERSONA_KEY);
        String firstConsumerToken = accessTokenProvider.createAccessToken(firstConsumerId, MemberRole.CONSUMER);
        long firstRequestId = createMatchingRequest(
                firstConsumerToken,
                "db/seed/scenarios/matching-price-vivaldi/request.json"
        );
        long offerId = jdbcTemplate.queryForObject(
                "select id from matching_offers order by id desc limit 1",
                Long.class
        );
        long groupId = jdbcTemplate.queryForObject(
                "select matching_request_group_id from matching_offers where id = ?",
                Long.class,
                offerId
        );
        long firstItemId = jdbcTemplate.queryForObject(
                "select id from matching_request_group_items where matching_request_id = ?",
                Long.class,
                firstRequestId
        );

        Long secondConsumerId = createConsumerPersona("consumer-group-b", "그룹강습생B");
        String secondConsumerToken = accessTokenProvider.createAccessToken(secondConsumerId, MemberRole.CONSUMER);
        long secondRequestId = createMatchingRequest(
                secondConsumerToken,
                "db/seed/scenarios/matching-no-candidate-alpensia/request.json"
        );
        jdbcTemplate.update("""
                insert into matching_request_group_items (
                    matching_request_group_id,
                    matching_request_id,
                    status,
                    created_at,
                    updated_at
                ) values (?, ?, 'NOT_REQUESTED', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, groupId, secondRequestId);
        jdbcTemplate.update(
                "update matching_requests set status = 'GROUPED' where id = ?",
                secondRequestId
        );
        long secondItemId = jdbcTemplate.queryForObject(
                "select id from matching_request_group_items where matching_request_id = ?",
                Long.class,
                secondRequestId
        );

        Map<String, List<Map<String, Object>>> beforeRead = matchingGraphSnapshot();
        JsonNode detail = getDetail(firstRequestId);

        assertResolved(detail, firstRequestId, "WAITING_FOR_INSTRUCTOR");
        assertThat(detail.at("/data/requestRelations").size()).isEqualTo(2);
        assertThat(findRequestRelation(detail, firstRequestId).path("consumerMemberId").asLong())
                .isEqualTo(firstConsumerId);
        assertThat(findRequestRelation(detail, firstRequestId).path("groupItemId").asLong())
                .isEqualTo(firstItemId);
        assertThat(findRequestRelation(detail, secondRequestId).path("consumerMemberId").asLong())
                .isEqualTo(secondConsumerId);
        assertThat(findRequestRelation(detail, secondRequestId).path("groupItemId").asLong())
                .isEqualTo(secondItemId);

        JsonNode instructorAccept = findAction(detail, "INSTRUCTOR_ACCEPT");
        assertThat(affectedMemberIds(instructorAccept)).containsExactlyInAnyOrder(
                instructorId,
                firstConsumerId,
                secondConsumerId
        );
        assertThat(actionResourceChangeKeys(instructorAccept)).containsExactlyInAnyOrder(
                "MATCHING_OFFER#" + offerId + ".status:OFFERED->ACCEPTED",
                "MATCHING_REQUEST_GROUP#" + groupId + ".status:EXPOSED->INSTRUCTOR_ACCEPTED",
                "MATCHING_REQUEST#" + firstRequestId + ".status:GROUPED->MATCHED",
                "MATCHING_REQUEST_GROUP_ITEM#" + firstItemId + ".status:NOT_REQUESTED->PENDING",
                "MATCHING_REQUEST#" + secondRequestId + ".status:GROUPED->MATCHED",
                "MATCHING_REQUEST_GROUP_ITEM#" + secondItemId + ".status:NOT_REQUESTED->PENDING"
        );
        assertThat(matchingGraphSnapshot()).isEqualTo(beforeRead);
    }

    private JsonNode getDetail(long matchingRequestId) throws Exception {
        return getJson("/dev/matching/requests/" + matchingRequestId);
    }

    private JsonNode getJson(String path) throws Exception {
        MvcResult result = mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private void assertResolved(JsonNode response, long matchingRequestId, String matchingStatus) {
        assertThat(response.at("/data/matchingRequestId").asLong()).isEqualTo(matchingRequestId);
        assertThat(response.at("/data/resolutionState").asText()).isEqualTo("RESOLVED");
        assertThat(response.at("/data/matchingStatus").asText()).isEqualTo(matchingStatus);
        assertThat(response.at("/data/diagnostics").isArray()).isTrue();
        assertThat(response.at("/data/diagnostics").isEmpty()).isTrue();
    }

    private Set<String> actionKeys(JsonNode response) {
        return response.at("/data/availableActions").valueStream()
                .map(action -> action.path("actionKey").asText())
                .collect(Collectors.toSet());
    }

    private JsonNode findAction(JsonNode response, String actionKey) {
        return response.at("/data/availableActions").valueStream()
                .filter(action -> actionKey.equals(action.path("actionKey").asText()))
                .findFirst()
                .orElseThrow();
    }

    private JsonNode findRequestRelation(JsonNode response, long matchingRequestId) {
        return response.at("/data/requestRelations").valueStream()
                .filter(relation -> relation.path("matchingRequestId").asLong() == matchingRequestId)
                .findFirst()
                .orElseThrow();
    }

    private Set<Long> affectedMemberIds(JsonNode action) {
        return action.path("affectedPeople").valueStream()
                .map(person -> person.path("memberId").asLong())
                .collect(Collectors.toSet());
    }

    private Set<String> actionResourceChangeKeys(JsonNode action) {
        return action.path("outcomes").valueStream()
                .flatMap(outcome -> outcome.path("resourceStateChanges").valueStream())
                .map(change -> {
                    JsonNode resource = change.path("resource");
                    String resourceId = resource.path("resourceId").isNull()
                            ? "null"
                            : resource.path("resourceId").asText();
                    return resource.path("resourceType").asText()
                            + "#" + resourceId
                            + "." + change.path("field").asText()
                            + ":" + change.path("before").asText()
                            + "->" + change.path("after").asText();
                })
                .collect(Collectors.toSet());
    }

    private Set<String> personaKeys(JsonNode response) {
        return response.at("/data/people").valueStream()
                .map(person -> person.path("personaKey").asText())
                .collect(Collectors.toSet());
    }

    private List<Long> resourceIds(JsonNode response, String resourceType) {
        return response.at("/data/resources").valueStream()
                .filter(resource -> resourceType.equals(resource.path("resourceType").asText()))
                .map(resource -> resource.path("resourceId").asLong())
                .toList();
    }

    private JsonNode findResource(JsonNode response, String resourceType, long resourceId) {
        return response.at("/data/resources").valueStream()
                .filter(resource -> resourceType.equals(resource.path("resourceType").asText()))
                .filter(resource -> resource.path("resourceId").asLong() == resourceId)
                .findFirst()
                .orElseThrow();
    }

    private JsonNode findListRequest(JsonNode response, long matchingRequestId) {
        return response.at("/data/requests").valueStream()
                .filter(request -> request.path("matchingRequestId").asLong() == matchingRequestId)
                .findFirst()
                .orElseThrow();
    }

    private Map<String, List<Map<String, Object>>> matchingGraphSnapshot() {
        Map<String, List<Map<String, Object>>> snapshot = new LinkedHashMap<>();
        snapshot.put("members", jdbcTemplate.queryForList("select * from members order by id"));
        snapshot.put("dev_personas", jdbcTemplate.queryForList("select * from dev_personas order by persona_key"));
        snapshot.put(
                "instructor_profiles",
                jdbcTemplate.queryForList("select * from instructor_profiles order by id")
        );
        snapshot.put(
                "matching_requests",
                jdbcTemplate.queryForList("select * from matching_requests order by id")
        );
        snapshot.put(
                "matching_requests_requested_duration_minutes",
                jdbcTemplate.queryForList("""
                        select *
                        from matching_requests_requested_duration_minutes
                        order by matching_request_id, duration_minutes
                        """)
        );
        snapshot.put(
                "matching_request_participants",
                jdbcTemplate.queryForList("select * from matching_request_participants order by id")
        );
        snapshot.put(
                "matching_request_groups",
                jdbcTemplate.queryForList("select * from matching_request_groups order by id")
        );
        snapshot.put(
                "matching_request_group_items",
                jdbcTemplate.queryForList("select * from matching_request_group_items order by id")
        );
        snapshot.put(
                "matching_offers",
                jdbcTemplate.queryForList("select * from matching_offers order by id")
        );
        snapshot.put(
                "matching_offer_price_snapshots",
                jdbcTemplate.queryForList("select * from matching_offer_price_snapshots order by id")
        );
        snapshot.put(
                "matching_request_price_snapshots",
                jdbcTemplate.queryForList("select * from matching_request_price_snapshots order by id")
        );
        snapshot.put(
                "matching_request_payments",
                jdbcTemplate.queryForList("select * from matching_request_payments order by id")
        );
        snapshot.put("lessons", jdbcTemplate.queryForList("select * from lessons order by id"));
        return snapshot;
    }

    private void cloneOfferPriceSnapshot(long sourceOfferId, long targetOfferId) {
        jdbcTemplate.update("""
                insert into matching_offer_price_snapshots (
                    additional_person_price_amount,
                    base_price_amount,
                    consumer_total_amount,
                    fee_rate_bps,
                    instructor_settlement_amount,
                    platform_fee_amount,
                    resort_pass_fee_amount,
                    total_headcount,
                    total_payment_amount,
                    created_at,
                    instructor_price_policy_id,
                    matching_offer_id,
                    platform_fee_policy_id
                )
                select
                    additional_person_price_amount,
                    base_price_amount,
                    consumer_total_amount,
                    fee_rate_bps,
                    instructor_settlement_amount,
                    platform_fee_amount,
                    resort_pass_fee_amount,
                    total_headcount,
                    total_payment_amount,
                    UTC_TIMESTAMP(6),
                    instructor_price_policy_id,
                    ?,
                    platform_fee_policy_id
                from matching_offer_price_snapshots
                where matching_offer_id = ?
                """, targetOfferId, sourceOfferId);
    }

    private long createAdditionalInstructorProfile(long sourceInstructorProfileId) {
        jdbcTemplate.update("""
                insert into members (nickname, profile_image_url, role, status, created_at, updated_at)
                values ('중복제안강사', null, 'INSTRUCTOR', 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """);
        long memberId = jdbcTemplate.queryForObject(
                "select id from members where nickname = '중복제안강사'",
                Long.class
        );
        jdbcTemplate.update("""
                insert into instructor_profiles (
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
                select
                    ?,
                    resort_id,
                    '중복제안강사',
                    '010-0000-9999',
                    gender,
                    birth_date,
                    intro,
                    career_start_date,
                    level,
                    certificate_type,
                    experience,
                    approval_status,
                    approved_at,
                    UTC_TIMESTAMP(6),
                    UTC_TIMESTAMP(6)
                from instructor_profiles
                where id = ?
                """, memberId, sourceInstructorProfileId);
        return jdbcTemplate.queryForObject(
                "select id from instructor_profiles where member_id = ?",
                Long.class,
                memberId
        );
    }

    private void insertPrematureLesson(long matchingRequestId, long offerId) {
        jdbcTemplate.update("""
                insert into lessons (
                    duration_minutes,
                    total_headcount,
                    confirmed_at,
                    created_at,
                    instructor_profile_id,
                    matching_offer_id,
                    resort_id,
                    scheduled_at,
                    updated_at,
                    lesson_level,
                    sport,
                    status
                )
                select
                    matchingRequestGroup.duration_minutes,
                    matchingRequest.headcount,
                    UTC_TIMESTAMP(6),
                    UTC_TIMESTAMP(6),
                    matchingOffer.instructor_profile_id,
                    matchingOffer.id,
                    matchingRequest.resort_id,
                    UTC_TIMESTAMP(6),
                    UTC_TIMESTAMP(6),
                    matchingRequest.lesson_level,
                    matchingRequest.sport,
                    'CONFIRMED'
                from matching_requests matchingRequest
                join matching_offers matchingOffer on matchingOffer.id = ?
                join matching_request_groups matchingRequestGroup
                  on matchingRequestGroup.id = matchingOffer.matching_request_group_id
                where matchingRequest.id = ?
                """, offerId, matchingRequestId);
    }

    private Long createConsumerPersona(String personaKey, String nickname) {
        jdbcTemplate.update("""
                insert into members (nickname, profile_image_url, role, status, created_at, updated_at)
                values (?, null, 'CONSUMER', 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, nickname);
        Long memberId = jdbcTemplate.queryForObject(
                "select id from members where nickname = ?",
                Long.class,
                nickname
        );
        jdbcTemplate.update("""
                insert into dev_personas (persona_key, member_id, template, created_at, updated_at)
                values (?, ?, 'GENERAL_CONSUMER', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, personaKey, memberId);
        return memberId;
    }

    private long createMatchingRequest(String consumerToken, String requestPath) throws Exception {
        String requestBody = new FileSystemResource(requestPath)
                .getContentAsString(StandardCharsets.UTF_8);
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

    private void cancelMatchingRequest(String consumerToken, long matchingRequestId) throws Exception {
        mockMvc.perform(post(
                        "/api/v1/consumer/matching-requests/{matchingRequestId}/cancellation",
                        matchingRequestId
                )
                        .header(HttpHeaders.AUTHORIZATION, bearer(consumerToken)))
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
        return objectMapper.readTree(result.getResponse().getContentAsByteArray())
                .at("/data/lessonId")
                .asLong();
    }

    private void confirmLessonStart(String token, long lessonId) throws Exception {
        mockMvc.perform(post("/api/v1/lessons/{lessonId}/start-confirmation", lessonId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
    }

    private Long personaMemberId(String personaKey) {
        return jdbcTemplate.queryForObject(
                "select member_id from dev_personas where persona_key = ?",
                Long.class,
                personaKey
        );
    }

    private void runSql(String path) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new FileSystemResource(path));
        populator.setSqlScriptEncoding(StandardCharsets.UTF_8.name());
        populator.execute(dataSource);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
