package org.sopt.ssingserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.auth.dev.repository.DevPersonaRepository;
import org.sopt.ssingserver.domain.auth.repository.OAuthAccountRepository;
import org.sopt.ssingserver.domain.auth.repository.RefreshTokenRepository;
import org.sopt.ssingserver.domain.instructor.repository.InstructorMatchingSettingRepository;
import org.sopt.ssingserver.domain.instructor.repository.InstructorPricePolicyRepository;
import org.sopt.ssingserver.domain.instructor.repository.InstructorProfileRepository;
import org.sopt.ssingserver.domain.lesson.repository.LessonParticipantRepository;
import org.sopt.ssingserver.domain.lesson.repository.LessonRepository;
import org.sopt.ssingserver.domain.lesson.repository.LessonCancellationRepository;
import org.sopt.ssingserver.domain.lesson.repository.LessonStartConfirmationRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingOfferRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestGroupItemRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestGroupRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestParticipantRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestRepository;
import org.sopt.ssingserver.domain.member.repository.MemberRepository;
import org.sopt.ssingserver.domain.notification.repository.FcmTokenRepository;
import org.sopt.ssingserver.domain.notification.repository.NotificationRepository;
import org.sopt.ssingserver.domain.payment.repository.MatchingOfferPriceSnapshotRepository;
import org.sopt.ssingserver.domain.payment.repository.MatchingRequestPaymentRepository;
import org.sopt.ssingserver.domain.payment.repository.MatchingRequestPriceSnapshotRepository;
import org.sopt.ssingserver.domain.payment.repository.PlatformFeePolicyRepository;
import org.sopt.ssingserver.domain.resort.repository.ResortRepository;
import org.sopt.ssingserver.domain.review.repository.ReviewRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ActiveProfiles({"test", "local"})
@SpringBootTest
@AutoConfigureMockMvc
class SsingServerApplicationTests {

	private static final Set<String> EXPECTED_OPEN_API_OPERATIONS = Set.of(
			"GET /dev/auth/personas",
			"POST /api/v1/consumer/auth/kakao",
			"POST /api/v1/instructor/auth/kakao",
			"POST /api/v1/auth/refresh",
			"POST /api/v1/auth/logout",
			"PUT /api/v1/fcm-tokens",
			"POST /api/v1/fcm-tokens/unregister",
			"GET /api/v1/notifications",
			"GET /api/v1/consumer/home",
			"GET /api/v1/instructor/home",
			"GET /api/v1/instructor/matching-exposure",
			"PUT /api/v1/instructor/matching-exposure",
			"POST /api/v1/instructor/matching-exposure/cancellation",
			"POST /api/v1/consumer/matching-requests",
			"GET /api/v1/consumer/matching-requests/active",
			"GET /api/v1/consumer/matching-requests/{matchingRequestId}",
			"POST /api/v1/consumer/matching-requests/{matchingRequestId}/cancellation",
			"PATCH /api/v1/consumer/matching-requests/{matchingRequestId}/confirmation",
			"POST /api/v1/consumer/matching-requests/{matchingRequestId}/payment",
			"GET /api/v1/instructor/matching-offers",
			"GET /api/v1/instructor/matching-offers/{offerId}",
			"PATCH /api/v1/instructor/matching-offers/{offerId}",
			"GET /api/v1/consumer/lessons/{lessonId}",
			"GET /api/v1/instructor/lessons/{lessonId}",
			"POST /api/v1/lessons/{lessonId}/start-confirmation",
			"POST /api/v1/lessons/{lessonId}/completion",
			"POST /api/v1/lessons/{lessonId}/cancellation"
	);
	private static final Set<String> PUBLIC_OPEN_API_OPERATIONS = Set.of(
			"GET /dev/auth/personas",
			"POST /api/v1/consumer/auth/kakao",
			"POST /api/v1/instructor/auth/kakao",
			"POST /api/v1/auth/refresh",
			"POST /api/v1/auth/logout"
	);
	private static final Set<String> HTTP_METHODS = Set.of(
			"get", "post", "put", "patch", "delete", "options", "head"
	);

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private OAuthAccountRepository oauthAccountRepository;

	@MockitoBean
	private DevPersonaRepository devPersonaRepository;

	@MockitoBean
	private MemberRepository memberRepository;

	@MockitoBean
	private ResortRepository resortRepository;

	@MockitoBean
	private RefreshTokenRepository refreshTokenRepository;

	@MockitoBean
	private InstructorProfileRepository instructorProfileRepository;

	@MockitoBean
	private InstructorMatchingSettingRepository instructorMatchingSettingRepository;

	@MockitoBean
	private InstructorPricePolicyRepository instructorPricePolicyRepository;

	@MockitoBean
	private LessonRepository lessonRepository;

	@MockitoBean
	private LessonParticipantRepository lessonParticipantRepository;

	@MockitoBean
	private LessonStartConfirmationRepository lessonStartConfirmationRepository;

	@MockitoBean
	private LessonCancellationRepository lessonCancellationRepository;

	@MockitoBean
	private MatchingRequestRepository matchingRequestRepository;

	@MockitoBean
	private MatchingRequestParticipantRepository matchingRequestParticipantRepository;

	@MockitoBean
	private MatchingRequestGroupRepository matchingRequestGroupRepository;

	@MockitoBean
	private MatchingRequestGroupItemRepository matchingRequestGroupItemRepository;

	@MockitoBean
	private MatchingOfferRepository matchingOfferRepository;

	@MockitoBean
	private MatchingRequestPaymentRepository matchingRequestPaymentRepository;

	@MockitoBean
	private MatchingOfferPriceSnapshotRepository matchingOfferPriceSnapshotRepository;

	@MockitoBean
	private MatchingRequestPriceSnapshotRepository matchingRequestPriceSnapshotRepository;

	@MockitoBean
	private PlatformFeePolicyRepository platformFeePolicyRepository;

	@MockitoBean
	private FcmTokenRepository fcmTokenRepository;

	@MockitoBean
	private NotificationRepository notificationRepository;

	@MockitoBean
	private ReviewRepository reviewRepository;

	@MockitoBean
	private PlatformTransactionManager transactionManager;

	@Test
	void contextLoads() {
	}

	@Test
	void generatedOpenApiReflectsSharedErrorResponseContract() throws Exception {
		JsonNode openApi = generatedOpenApi();

		assertThat(openApi.path("paths").has("/dev/auth/console")).isFalse();
		JsonNode responses = openApi.path("paths")
				.path("/api/v1/instructor/matching-exposure")
				.path("put")
				.path("responses");
		assertThat(responses.has("200")).isTrue();
		assertThat(responses.path("400")
				.path("content")
				.path("application/json")
				.path("examples")
				.has("VALIDATION_FAILED")).isTrue();
		assertThat(responses.path("400")
				.path("content")
				.path("application/json")
				.path("examples")
				.has("BAD_REQUEST")).isTrue();
		assertThat(responses.path("500")
				.path("content")
				.path("application/json")
				.path("examples")
				.has("INTERNAL_ERROR")).isTrue();
		assertThat(openApi.path("components").path("schemas").has("CommonErrorResponse")).isTrue();
		assertThat(openApi.path("components").path("schemas").has("ValidationErrorResponse")).isTrue();

		JsonNode matchingStatusResponses = openApi.path("paths")
				.path("/api/v1/consumer/matching-requests/{matchingRequestId}")
				.path("get")
				.path("responses");
		assertThat(matchingStatusResponses.path("400")
				.path("content")
				.path("application/json")
				.path("examples")
				.has("BAD_REQUEST")).isTrue();
	}

	@Test
	void generatedOpenApiReflectsSharedSuccessResponseContract() throws Exception {
		JsonNode openApi = generatedOpenApi();

		collectOperations(openApi.path("paths")).forEach(operationKey -> {
			JsonNode operation = findOperation(openApi, operationKey);
			operation.path("responses").forEachEntry((responseCode, response) -> {
				if (!responseCode.matches("2\\d\\d") || "204".equals(responseCode)) {
					return;
				}

				assertThat(response.path("content").has("application/json"))
						.as(operationKey + " " + responseCode + " application/json")
						.isTrue();
				response.path("content").forEachEntry((mediaTypeName, mediaType) -> {
					JsonNode schemaProperties = mediaType.path("schema").path("properties");
					assertThat(schemaProperties.has("success"))
							.as(operationKey + " " + responseCode + " " + mediaTypeName + " success")
							.isTrue();
					assertThat(schemaProperties.has("code"))
							.as(operationKey + " " + responseCode + " " + mediaTypeName + " code")
							.isTrue();
					assertThat(schemaProperties.has("message"))
							.as(operationKey + " " + responseCode + " " + mediaTypeName + " message")
							.isTrue();
					assertThat(schemaProperties.has("errors"))
							.as(operationKey + " " + responseCode + " " + mediaTypeName + " errors")
							.isFalse();
					assertThat(schemaProperties.has("requestId"))
							.as(operationKey + " " + responseCode + " " + mediaTypeName + " requestId")
							.isFalse();
					assertThat(schemaProperties.path("code").has("example"))
							.as(operationKey + " " + responseCode + " " + mediaTypeName + " code example")
							.isFalse();
					assertThat(schemaProperties.path("message").has("example"))
							.as(operationKey + " " + responseCode + " " + mediaTypeName + " message example")
							.isFalse();
				});
			});
		});
	}

	@Test
	void generatedOpenApiDocumentsNotificationListContract() throws Exception {
		JsonNode openApi = generatedOpenApi();
		JsonNode operation = findOperation(openApi, "GET /api/v1/notifications");
		JsonNode responses = operation.path("responses");
		JsonNode successMediaType = responses.path("200").path("content").path("application/json");
		JsonNode schemas = openApi.path("components").path("schemas");
		JsonNode listSchema = schemas.path("NotificationListResponse");
		JsonNode itemSchema = schemas.path("NotificationItemResponse");

		assertThat(successMediaType.path("schema").path("properties").path("data").path("$ref").asString())
				.isEqualTo("#/components/schemas/NotificationListResponse");
		assertThat(fieldNames(listSchema.path("properties")))
				.containsExactlyInAnyOrder("notifications", "nextCursor", "hasNext");
		assertThat(notificationTextValues(listSchema.path("required")))
				.contains("notifications", "hasNext")
				.doesNotContain("nextCursor");
		assertThat(listSchema.path("properties").path("notifications").path("items").path("$ref").asString())
				.isEqualTo("#/components/schemas/NotificationItemResponse");

		assertThat(fieldNames(itemSchema.path("properties"))).containsExactlyInAnyOrder(
				"notificationId",
				"type",
				"title",
				"body",
				"isRead",
				"createdAt"
		);
		assertThat(notificationTextValues(itemSchema.path("required"))).containsExactlyInAnyOrder(
				"notificationId",
				"type",
				"title",
				"body",
				"isRead",
				"createdAt"
		);
		assertThat(notificationTextValues(itemSchema.path("properties").path("type").path("enum")))
				.containsExactlyInAnyOrder(
						"MATCHING_OFFER_RECEIVED",
						"MATCHING_OFFER_CLOSED",
						"MATCHING_CONFIRMED"
				);
		assertThat(itemSchema.path("properties").path("createdAt").path("format").asString())
				.isEqualTo("date-time");
		assertThat(itemSchema.path("properties").path("createdAt").path("example").asString())
				.endsWith("Z");

		JsonNode cursorParameter = findParameter(operation, "cursor");
		JsonNode sizeParameter = findParameter(operation, "size");
		assertThat(cursorParameter.path("required").asBoolean()).isFalse();
		assertThat(cursorParameter.path("example").asString()).isEqualTo("2026-07-04T12:59:00Z_99");
		assertThat(sizeParameter.path("required").asBoolean()).isFalse();
		assertThat(sizeParameter.path("schema").path("default").asInt()).isEqualTo(20);
		assertThat(sizeParameter.path("schema").path("minimum").asInt()).isEqualTo(1);
		assertThat(sizeParameter.path("schema").path("maximum").asInt()).isEqualTo(100);

		assertThat(fieldNames(responses)).containsExactlyInAnyOrder("200", "400", "401", "403", "500");
		assertThat(fieldNames(responses.path("400").path("content").path("application/json").path("examples")))
				.containsExactlyInAnyOrder("VALIDATION_FAILED", "BAD_REQUEST");
		assertThat(fieldNames(responses.path("401").path("content").path("application/json").path("examples")))
				.containsExactlyInAnyOrder("UNAUTHENTICATED", "AUTH_INVALID_TOKEN", "AUTH_TOKEN_EXPIRED");
		assertThat(fieldNames(responses.path("403").path("content").path("application/json").path("examples")))
				.containsExactly("FORBIDDEN");
		assertThat(fieldNames(responses.path("500").path("content").path("application/json").path("examples")))
				.containsExactly("INTERNAL_ERROR");

		JsonNode successExamples = successMediaType.path("examples");
		assertThat(fieldNames(successExamples)).containsExactlyInAnyOrder("PAGE_WITH_NEXT", "LAST_PAGE");
		assertThat(successExamples.path("PAGE_WITH_NEXT").path("value").path("data")
				.path("nextCursor").asString()).isEqualTo("2026-07-04T12:59:00Z_99");
		assertThat(successExamples.path("PAGE_WITH_NEXT").path("value").path("data")
				.path("hasNext").asBoolean()).isTrue();
		assertThat(successExamples.path("LAST_PAGE").path("value").path("data")
				.path("notifications").isEmpty()).isTrue();
		assertThat(successExamples.path("LAST_PAGE").path("value").path("data")
				.path("hasNext").asBoolean()).isFalse();
	}

	@Test
	void generatedOpenApiDocumentsAuthAndHidesUnsafeDevOperations() throws Exception {
		JsonNode openApi = generatedOpenApi();
		JsonNode paths = openApi.path("paths");

		assertThat(paths.has("/dev/auth/token")).isFalse();
		assertThat(paths.path("/dev/auth/personas").has("get")).isTrue();
		assertThat(paths.path("/dev/auth/personas").has("post")).isFalse();

		JsonNode consumerLogin = paths.path("/api/v1/consumer/auth/kakao").path("post");
		assertThat(consumerLogin.has("security")).isFalse();
		assertThat(consumerLogin.path("responses").has("200")).isTrue();
		assertThat(consumerLogin.path("responses")
				.path("500")
				.path("content")
				.path("application/json")
				.path("examples")
				.has("INTERNAL_ERROR")).isTrue();

		JsonNode logout = paths.path("/api/v1/auth/logout").path("post");
		assertThat(logout.path("responses").has("204")).isTrue();
		assertThat(logout.path("responses").path("204").has("content")).isFalse();

		JsonNode schemas = openApi.path("components").path("schemas");
		assertThat(schemas.has("ConsumerAuthMemberResponse")).isTrue();
		assertThat(schemas.has("InstructorAuthMemberResponse")).isTrue();
	}

	@Test
	void generatedOpenApiDocumentsLessonStatusVariants() throws Exception {
		JsonNode openApi = generatedOpenApi();
		JsonNode schemas = openApi.path("components").path("schemas");

		assertThat(schemaReferences(schemas.path("ConsumerLessonDetailResponse").path("oneOf")))
				.contains(
						"#/components/schemas/ConsumerLessonConfirmedDetail",
						"#/components/schemas/ConsumerLessonInProgressDetail",
						"#/components/schemas/ConsumerLessonCompletedDetail",
						"#/components/schemas/ConsumerLessonCanceledDetail"
				);
		assertDiscriminatorMapping(
				schemas,
				"ConsumerLessonDetailResponse",
				"CONFIRMED",
				"#/components/schemas/ConsumerLessonConfirmedDetail"
		);
		assertDiscriminatorMapping(
				schemas,
				"ConsumerLessonDetailResponse",
				"IN_PROGRESS",
				"#/components/schemas/ConsumerLessonInProgressDetail"
		);
		assertDiscriminatorMapping(
				schemas,
				"ConsumerLessonDetailResponse",
				"COMPLETED",
				"#/components/schemas/ConsumerLessonCompletedDetail"
		);
		assertDiscriminatorMapping(
				schemas,
				"ConsumerLessonDetailResponse",
				"CANCELED",
				"#/components/schemas/ConsumerLessonCanceledDetail"
		);
		assertThat(schemaReferences(schemas.path("InstructorLessonDetailResponse").path("oneOf")))
				.contains(
						"#/components/schemas/InstructorLessonConfirmedDetail",
						"#/components/schemas/InstructorLessonInProgressDetail",
						"#/components/schemas/InstructorLessonCompletedDetail",
						"#/components/schemas/InstructorLessonCanceledDetail"
				);
		assertDiscriminatorMapping(
				schemas,
				"InstructorLessonDetailResponse",
				"CONFIRMED",
				"#/components/schemas/InstructorLessonConfirmedDetail"
		);
		assertDiscriminatorMapping(
				schemas,
				"InstructorLessonDetailResponse",
				"IN_PROGRESS",
				"#/components/schemas/InstructorLessonInProgressDetail"
		);
		assertDiscriminatorMapping(
				schemas,
				"InstructorLessonDetailResponse",
				"COMPLETED",
				"#/components/schemas/InstructorLessonCompletedDetail"
		);
		assertDiscriminatorMapping(
				schemas,
				"InstructorLessonDetailResponse",
				"CANCELED",
				"#/components/schemas/InstructorLessonCanceledDetail"
		);
		assertThat(schemaReferences(schemas.path("LessonStartConfirmationResponse").path("oneOf")))
				.contains(
						"#/components/schemas/LessonStartConfirmationPending",
						"#/components/schemas/LessonStartConfirmationStarted"
				);
		assertDiscriminatorMapping(
				schemas,
				"LessonStartConfirmationResponse",
				"CONFIRMED",
				"#/components/schemas/LessonStartConfirmationPending"
		);
		assertDiscriminatorMapping(
				schemas,
				"LessonStartConfirmationResponse",
				"IN_PROGRESS",
				"#/components/schemas/LessonStartConfirmationStarted"
		);

		JsonNode consumerLessonContent = findOperation(openApi, "GET /api/v1/consumer/lessons/{lessonId}")
				.path("responses")
				.path("200")
				.path("content");
		assertThat(consumerLessonContent.has("application/json")).isTrue();
		JsonNode consumerLessonExamples = consumerLessonContent.path("application/json").path("examples");
		assertThat(consumerLessonExamples.has("CONFIRMED")).isTrue();
		assertThat(consumerLessonExamples.has("IN_PROGRESS")).isTrue();
		assertThat(consumerLessonExamples.has("COMPLETED")).isTrue();
		assertThat(consumerLessonExamples.has("CANCELED")).isTrue();
		assertThat(consumerLessonExamples.path("CONFIRMED").path("value").path("data").path("lessonStatus").asString())
				.isEqualTo("CONFIRMED");
		assertThat(consumerLessonExamples.path("COMPLETED").path("value").path("data").has("statusInfo"))
				.isFalse();
		assertThat(consumerLessonExamples.path("CONFIRMED").path("value").path("data")
				.path("statusInfo").path("confirmedCount").asInt()).isEqualTo(4);
		assertThat(consumerLessonExamples.path("CONFIRMED").path("value").path("data")
				.path("statusInfo").path("requiredCount").asInt()).isEqualTo(6);

		JsonNode instructorLessonExamples = findOperation(openApi, "GET /api/v1/instructor/lessons/{lessonId}")
				.path("responses")
				.path("200")
				.path("content")
				.path("application/json")
				.path("examples");
		assertThat(instructorLessonExamples.path("CONFIRMED").path("value").path("data")
				.path("statusInfo").path("confirmedCount").asInt()).isEqualTo(4);
		assertThat(instructorLessonExamples.path("CONFIRMED").path("value").path("data")
				.path("statusInfo").path("requiredCount").asInt()).isEqualTo(6);

		JsonNode startConfirmationContent = findOperation(openApi, "POST /api/v1/lessons/{lessonId}/start-confirmation")
				.path("responses")
				.path("200")
				.path("content");
		assertThat(startConfirmationContent.has("application/json")).isTrue();
		JsonNode startConfirmationExamples = startConfirmationContent.path("application/json").path("examples");
		assertThat(startConfirmationExamples.has("CONFIRMED_PENDING")).isTrue();
		assertThat(startConfirmationExamples.has("IN_PROGRESS_STARTED")).isTrue();
		assertThat(startConfirmationExamples.path("IN_PROGRESS_STARTED")
				.path("value")
				.path("data")
				.path("startedAt")
				.asString()).isNotBlank();
		assertThat(startConfirmationExamples.path("CONFIRMED_PENDING").path("value").path("data")
				.path("statusInfo").path("confirmedCount").asInt()).isEqualTo(4);
		assertThat(startConfirmationExamples.path("CONFIRMED_PENDING").path("value").path("data")
				.path("statusInfo").path("requiredCount").asInt()).isEqualTo(6);
	}

	@Test
	void generatedOpenApiDocumentsMatchingRecoveryContracts() throws Exception {
		JsonNode openApi = generatedOpenApi();
		JsonNode schemas = openApi.path("components").path("schemas");

		assertThat(schemaReferences(schemas.path("ConsumerActiveMatchingResponse").path("oneOf")))
				.containsExactlyInAnyOrder(
						"#/components/schemas/ConsumerActiveMatchingActive",
						"#/components/schemas/ConsumerActiveMatchingNone"
				);
		assertRecoveryDiscriminatorMapping(
				schemas,
				"ConsumerActiveMatchingResponse",
				"ACTIVE",
				"#/components/schemas/ConsumerActiveMatchingActive"
		);
		assertRecoveryDiscriminatorMapping(
				schemas,
				"ConsumerActiveMatchingResponse",
				"NONE",
				"#/components/schemas/ConsumerActiveMatchingNone"
		);
		JsonNode consumerBaseSchema = schemas.path("ConsumerActiveMatchingResponse");
		assertThat(consumerBaseSchema.path("properties").has("recoveryState")).isTrue();
		assertThat(textValues(consumerBaseSchema.path("required"))).contains("recoveryState");
		assertThat(textValues(consumerBaseSchema.path("properties").path("recoveryState").path("enum")))
				.containsExactlyInAnyOrder("ACTIVE", "NONE");

		JsonNode consumerActiveProperties = schemas.path("ConsumerActiveMatchingActive").path("properties");
		assertThat(consumerActiveProperties.has("recoveryState")).isTrue();
		assertThat(consumerActiveProperties.has("matchingRequestId")).isTrue();
		assertThat(consumerActiveProperties.has("matchingStatus")).isTrue();
		assertThat(consumerActiveProperties.has("requestStatus")).isTrue();
		assertThat(consumerActiveProperties.has("requestSummary")).isTrue();
		assertThat(consumerActiveProperties.has("lessonSummary")).isTrue();
		assertThat(consumerActiveProperties.has("instructorProfile")).isTrue();
		assertThat(consumerActiveProperties.has("progressSummary")).isTrue();
		assertThat(consumerActiveProperties.has("priceSummary")).isTrue();
		assertThat(consumerActiveProperties.has("expiresAt")).isFalse();
		assertThat(consumerActiveProperties.has("lessonId")).isFalse();
		assertThat(consumerActiveProperties.has("payload")).isFalse();
		assertThat(textValues(schemas.path("ConsumerActiveMatchingActive").path("required")))
				.contains(
						"recoveryState",
						"matchingRequestId",
						"matchingStatus",
						"requestStatus",
						"requestSummary"
				);
		assertThat(textValues(consumerActiveProperties.path("recoveryState").path("enum")))
				.containsExactly("ACTIVE");
		assertThat(textValues(consumerActiveProperties.path("requestStatus").path("enum")))
				.containsExactlyInAnyOrder("REQUESTED", "GROUPED", "MATCHED");
		assertThat(schemas.path("ConsumerActiveMatchingRequestSummary").path("properties")
				.has("resort")).isTrue();
		assertThat(schemas.path("ConsumerActiveMatchingRequestSummary").path("properties")
				.has("headcount")).isTrue();
		assertThat(schemas.path("ConsumerActiveMatchingResort").path("properties")
				.has("displayName")).isTrue();
		assertThat(schemas.path("ConsumerActiveMatchingLessonSummary").path("properties")
				.has("totalHeadcount")).isTrue();
		assertThat(schemas.path("ConsumerActiveMatchingInstructorProfile").path("properties")
				.has("careerYears")).isTrue();
		assertThat(schemas.path("ConsumerActiveMatchingInstructorProfile").path("properties")
				.has("completedLessonCount")).isTrue();
		assertThat(schemas.path("ConsumerActiveMatchingInstructorProfile").path("properties")
				.has("certificateTypes")).isTrue();
		assertThat(schemas.path("ConsumerActiveMatchingLatestReview").path("properties")
				.has("content")).isTrue();
		JsonNode legacyInstructorProfileProperties = schemas
				.path("ConsumerMatchingStatusInstructorProfile")
				.path("properties");
		assertThat(legacyInstructorProfileProperties.has("level")).isTrue();
		assertThat(legacyInstructorProfileProperties.has("careerYears")).isFalse();
		assertThat(legacyInstructorProfileProperties.has("completedLessonCount")).isFalse();
		assertThat(legacyInstructorProfileProperties.has("certificateTypes")).isFalse();
		JsonNode consumerNoneProperties = schemas.path("ConsumerActiveMatchingNone").path("properties");
		assertThat(consumerNoneProperties.has("recoveryState")).isTrue();
		assertThat(consumerNoneProperties.size()).isEqualTo(1);
		assertThat(textValues(schemas.path("ConsumerActiveMatchingNone").path("required")))
				.contains("recoveryState");
		assertThat(textValues(consumerNoneProperties.path("recoveryState").path("enum")))
				.containsExactly("NONE");

		JsonNode consumerOperation = findOperation(
				openApi,
				"GET /api/v1/consumer/matching-requests/active"
		);
		JsonNode consumerResponses = consumerOperation.path("responses");
		assertThat(consumerResponses.has("200")).isTrue();
		assertThat(consumerResponses.has("204")).isFalse();
		assertThat(consumerResponses.path("200")
				.path("content")
				.path("application/json")
				.path("schema")
				.path("properties")
				.path("data")
				.path("$ref")
				.asString()).isEqualTo("#/components/schemas/ConsumerActiveMatchingResponse");
		JsonNode consumerExamples = consumerResponses.path("200")
				.path("content")
				.path("application/json")
				.path("examples");
		assertThat(fieldNames(consumerExamples)).containsExactlyInAnyOrder(
				"SEARCHING",
				"WAITING_FOR_TEAM",
				"WAITING_FOR_INSTRUCTOR",
				"REMATCHING",
				"WAITING_FOR_CONFIRMATION",
				"WAITING_FOR_OTHER_CONFIRMATIONS",
				"PAYMENT_PENDING",
				"WAITING_FOR_OTHER_PAYMENTS",
				"NONE"
		);
		JsonNode searchingExample = consumerExamples.path("SEARCHING").path("value").path("data");
		assertThat(searchingExample.path("recoveryState").asString()).isEqualTo("ACTIVE");
		assertThat(searchingExample.path("requestSummary").path("headcount").asInt()).isEqualTo(2);
		assertThat(searchingExample.has("groupId")).isFalse();
		assertThat(searchingExample.has("lessonSummary")).isFalse();
		assertThat(searchingExample.has("instructorProfile")).isFalse();
		assertThat(searchingExample.has("progressSummary")).isFalse();
		assertThat(searchingExample.has("priceSummary")).isFalse();
		assertThat(searchingExample.has("estimatedLessonPriceAmount")).isFalse();
		JsonNode rematchingExample = consumerExamples.path("REMATCHING").path("value").path("data");
		assertThat(rematchingExample.path("matchingStatus").asString()).isEqualTo("REMATCHING");
		assertThat(rematchingExample.path("requestStatusReason").asString())
				.isEqualTo("CONSUMER_REJECTED_INSTRUCTOR");
		assertThat(rematchingExample.has("groupId")).isFalse();
		assertThat(rematchingExample.has("offerStatus")).isFalse();
		assertThat(rematchingExample.has("paymentStatus")).isFalse();
		JsonNode confirmationExample = consumerExamples
				.path("WAITING_FOR_CONFIRMATION")
				.path("value")
				.path("data");
		assertThat(confirmationExample.path("lessonSummary").path("startType").asString())
				.isEqualTo("IMMEDIATE");
		assertThat(confirmationExample.path("instructorProfile").path("careerYears").asInt())
				.isEqualTo(6);
		assertThat(confirmationExample.has("paymentStatus")).isFalse();
		JsonNode paymentExample = consumerExamples.path("PAYMENT_PENDING").path("value").path("data");
		assertThat(paymentExample.path("paymentStatus").asString()).isEqualTo("PENDING");
		assertThat(paymentExample.path("progressSummary").path("paidRequesterCount").asInt())
				.isZero();
		for (String exampleName : List.of(
				"SEARCHING",
				"WAITING_FOR_TEAM",
				"WAITING_FOR_INSTRUCTOR",
				"REMATCHING",
				"WAITING_FOR_CONFIRMATION",
				"WAITING_FOR_OTHER_CONFIRMATIONS",
				"PAYMENT_PENDING",
				"WAITING_FOR_OTHER_PAYMENTS"
		)) {
			JsonNode activeExample = consumerExamples.path(exampleName).path("value").path("data");
			assertThat(activeExample.has("requestSummary")).isTrue();
			assertThat(activeExample.has("expiresAt")).isFalse();
			assertThat(activeExample.has("lessonId")).isFalse();
			assertThat(activeExample.has("payload")).isFalse();
		}
		assertThat(consumerExamples.path("NONE").path("value").path("data")
				.path("recoveryState").asString()).isEqualTo("NONE");
		assertThat(consumerExamples.path("NONE").path("value").path("data").size()).isEqualTo(1);
		assertThat(findOperation(openApi, "GET /api/v1/consumer/matching-requests/{matchingRequestId}")
				.path("responses")
				.path("200")
				.path("content")
				.path("application/json")
				.path("schema")
				.path("properties")
				.path("data")
				.path("$ref")
				.asString()).isEqualTo("#/components/schemas/ConsumerMatchingStatusResponse");

		JsonNode exposureOperation = findOperation(
				openApi,
				"PUT /api/v1/instructor/matching-exposure"
		);
		assertThat(exposureOperation.path("responses")
				.path("200")
				.path("content")
				.path("application/json")
				.path("schema")
				.path("properties")
				.path("data")
				.path("$ref")
				.asString()).isEqualTo("#/components/schemas/InstructorMatchingExposureResponse");
		JsonNode exposureSchema = schemas.path("InstructorMatchingExposureResponse");
		JsonNode exposureProperties = exposureSchema.path("properties");
		assertThat(fieldNames(exposureProperties)).containsExactlyInAnyOrder(
				"isExposed",
				"estimatedLessonPriceAmount",
				"pricePolicy"
		);
		assertThat(exposureProperties.path("estimatedLessonPriceAmount").path("example").asInt())
				.isEqualTo(87_500);
		assertThat(exposureProperties.path("pricePolicy").path("$ref").asString())
				.isEqualTo("#/components/schemas/InstructorPricePolicy");
		assertThat(textValues(exposureSchema.path("required"))).contains(
				"isExposed",
				"estimatedLessonPriceAmount",
				"pricePolicy"
		);

		JsonNode currentOffersOperation = findOperation(
				openApi,
				"GET /api/v1/instructor/matching-offers"
		);
		assertThat(currentOffersOperation.path("parameters").size()).isZero();
		JsonNode currentOffersResponses = currentOffersOperation.path("responses");
		assertThat(currentOffersResponses.has("400")).isFalse();
		assertThat(currentOffersResponses.path("404")
				.path("content")
				.path("application/json")
				.path("examples")
				.has("NOT_FOUND")).isTrue();
		assertThat(currentOffersResponses.path("409")
				.path("content")
				.path("application/json")
				.path("examples")
				.has("MATCHING_NOT_ACTIVE")).isTrue();
		assertThat(currentOffersResponses.path("200")
				.path("content")
				.path("application/json")
				.path("schema")
				.path("properties")
				.path("data")
				.path("$ref")
				.asString()).isEqualTo("#/components/schemas/InstructorMatchingOffersResponse");

		JsonNode currentOffersSchema = schemas.path("InstructorMatchingOffersResponse");
		JsonNode currentOffersProperties = currentOffersSchema.path("properties");
		assertThat(fieldNames(currentOffersProperties)).containsExactlyInAnyOrder(
				"offerId",
				"matchingSetting"
		);
		assertThat(currentOffersProperties.has("activeOffer")).isFalse();
		assertThat(textValues(currentOffersSchema.path("required"))).contains(
				"offerId",
				"matchingSetting"
		);
		JsonNode offerIdSchema = currentOffersProperties.path("offerId");
		boolean nullableOfferId = offerIdSchema.path("nullable").asBoolean()
				|| offerIdSchema.path("type").toString().matches(".*\\\"null\\\".*")
				|| offerIdSchema.path("types").toString().matches(".*\\\"null\\\".*")
				|| offerIdSchema.path("anyOf").toString().matches(".*\\\"null\\\".*");
		assertThat(nullableOfferId).isTrue();
		assertThat(currentOffersProperties.path("matchingSetting").path("$ref").asString())
				.isEqualTo("#/components/schemas/InstructorMatchingWaitingSetting");

		JsonNode matchingSettingSchema = schemas.path("InstructorMatchingWaitingSetting");
		JsonNode matchingSettingProperties = matchingSettingSchema.path("properties");
		assertThat(fieldNames(matchingSettingProperties)).containsExactlyInAnyOrder(
				"isExposed",
				"resort",
				"sport",
				"lessonLevels",
				"availableDurationMinutes",
				"maxHeadcount",
				"equipmentReady",
				"estimatedLessonPriceAmount",
				"pricePolicy"
		);
		assertThat(matchingSettingProperties.path("estimatedLessonPriceAmount").path("example").asInt())
				.isEqualTo(105_000);
		assertThat(matchingSettingProperties.path("pricePolicy").path("$ref").asString())
				.isEqualTo("#/components/schemas/InstructorPricePolicy");
		assertThat(textValues(matchingSettingSchema.path("required"))).contains(
				"isExposed",
				"resort",
				"sport",
				"lessonLevels",
				"availableDurationMinutes",
				"maxHeadcount",
				"equipmentReady",
				"estimatedLessonPriceAmount",
				"pricePolicy"
		);

		JsonNode currentOffersExamples = currentOffersResponses.path("200")
				.path("content")
				.path("application/json")
				.path("examples");
		assertThat(fieldNames(currentOffersExamples)).containsExactlyInAnyOrder("WAITING", "OFFERED");
		JsonNode waitingOfferExample = currentOffersExamples.path("WAITING").path("value").path("data");
		assertThat(waitingOfferExample.has("offerId")).isTrue();
		assertThat(waitingOfferExample.path("offerId").isNull()).isTrue();
		assertThat(waitingOfferExample.path("matchingSetting").path("isExposed").asBoolean()).isTrue();
		assertThat(waitingOfferExample.path("matchingSetting")
				.path("estimatedLessonPriceAmount").asInt()).isEqualTo(105_000);
		assertThat(waitingOfferExample.path("matchingSetting").path("pricePolicy")
				.path("basePriceAmount").asInt()).isEqualTo(60_000);
		assertThat(waitingOfferExample.has("activeOffer")).isFalse();
		JsonNode offeredOfferExample = currentOffersExamples.path("OFFERED").path("value").path("data");
		assertThat(offeredOfferExample.path("offerId").asLong()).isEqualTo(21L);
		assertThat(offeredOfferExample.has("matchingSetting")).isTrue();
		assertThat(offeredOfferExample.has("activeOffer")).isFalse();

		JsonNode instructorBaseSchema = schemas.path("InstructorMatchingOfferDetailResponse");
		assertThat(instructorBaseSchema.has("oneOf")).isFalse();
		assertThat(instructorBaseSchema.has("discriminator")).isFalse();

		JsonNode detailOperation = findOperation(
				openApi,
				"GET /api/v1/instructor/matching-offers/{offerId}"
		);
		JsonNode detailResponses = detailOperation.path("responses");
		assertThat(detailResponses.has("200")).isTrue();
		assertThat(detailResponses.path("400")
				.path("content")
				.path("application/json")
				.path("examples")
				.has("BAD_REQUEST")).isTrue();
		assertThat(detailResponses.path("404")
				.path("content")
				.path("application/json")
				.path("examples")
				.has("MATCHING_OFFER_NOT_FOUND")).isTrue();
		assertThat(detailResponses.path("409")
				.path("content")
				.path("application/json")
				.path("examples")
				.has("MATCHING_NOT_ACTIVE")).isTrue();

		assertThat(detailResponses.path("200")
				.path("content")
				.path("application/json")
				.path("schema")
				.path("properties")
				.path("data")
				.path("$ref")
				.asString()).isEqualTo("#/components/schemas/InstructorMatchingOfferDetailResponse");
		JsonNode detailExamples = detailResponses.path("200")
				.path("content")
				.path("application/json")
				.path("examples");
		assertThat(fieldNames(detailExamples)).containsExactly("AVAILABLE");
		assertThat(detailExamples.path("AVAILABLE").path("value").path("data")
				.path("recoveryState").asString()).isEqualTo("AVAILABLE");

		JsonNode instructorAvailableSchema = schemas.path("InstructorMatchingOfferDetailResponse");
		assertThat(instructorAvailableSchema.path("description").asString())
				.contains("종료된 매칭은 MATCHING_NOT_ACTIVE 오류로 반환");
		JsonNode detailProperties = instructorAvailableSchema.path("properties");
		assertThat(detailProperties.has("recoveryState")).isTrue();
		assertThat(detailProperties.has("offerId")).isTrue();
		assertThat(detailProperties.has("groupId")).isTrue();
		assertThat(detailProperties.has("matchingStatus")).isTrue();
		assertThat(detailProperties.has("requestSummary")).isTrue();
		assertThat(detailProperties.has("lessonSummary")).isTrue();
		assertThat(detailProperties.has("priceSummary")).isTrue();
		assertThat(detailProperties.path("priceSummary").path("$ref").asString())
				.isEqualTo("#/components/schemas/InstructorMatchingPriceSummary");
		JsonNode instructorPriceProperties = schemas.path("InstructorMatchingPriceSummary").path("properties");
		assertThat(fieldNames(instructorPriceProperties)).containsExactly("instructorSettlementAmount");
		assertThat(detailProperties.has("participants")).isTrue();
		assertThat(textValues(schemas.path("InstructorMatchingOfferDetailResponse").path("required")))
				.containsExactlyInAnyOrder(
						"recoveryState",
						"offerId",
						"groupId",
						"offerStatus",
						"groupStatus",
						"matchingStatus",
						"requestSummary",
						"lessonSummary",
						"priceSummary",
						"participants"
				);
		assertThat(textValues(detailProperties.path("recoveryState").path("enum")))
				.containsExactly("AVAILABLE");
		assertThat(textValues(detailProperties.path("offerStatus").path("enum")))
				.containsExactlyInAnyOrder("OFFERED", "ACCEPTED");
		assertThat(textValues(detailProperties.path("groupStatus").path("enum")))
				.containsExactlyInAnyOrder("EXPOSED", "INSTRUCTOR_ACCEPTED", "PAYMENT_PENDING");
		assertThat(textValues(detailProperties.path("matchingStatus").path("enum")))
				.containsExactlyInAnyOrder(
						"WAITING_FOR_INSTRUCTOR",
						"WAITING_FOR_CONFIRMATION",
						"PAYMENT_PENDING"
				);
		JsonNode participantItems = detailProperties.path("participants").path("items");
		assertThat(participantItems.path("$ref").asString())
				.isEqualTo("#/components/schemas/InstructorMatchingOfferParticipant");
		JsonNode participantProperties = schemas.path("InstructorMatchingOfferParticipant").path("properties");
		assertThat(participantProperties.has("age")).isTrue();
		assertThat(participantProperties.has("gender")).isTrue();
		assertThat(participantProperties.size()).isEqualTo(2);
		assertThat(detailProperties.has("expiresAt")).isFalse();
		assertThat(schemas.has("InstructorMatchingOfferDetailAvailable")).isFalse();
		assertThat(schemas.has("InstructorMatchingOfferDetailStale")).isFalse();
	}

	@Test
	void generatedOpenApiDocumentsInstructorHomeFlowIdentifiers() throws Exception {
		JsonNode openApi = generatedOpenApi();
		JsonNode homeOperation = findOperation(openApi, "GET /api/v1/instructor/home");
		assertThat(homeOperation.path("description").asString())
				.contains("CONFIRMED/IN_PROGRESS", "offerId", "lessonId");

		JsonNode cardSchema = openApi.path("components")
				.path("schemas")
				.path("InstructorHomeLessonCardResponse");
		JsonNode cardProperties = cardSchema.path("properties");
		assertThat(cardProperties.has("offerId")).isTrue();
		assertThat(cardProperties.path("offerId").path("description").asString())
				.contains("동일 매칭 흐름", "CONFIRMED/IN_PROGRESS");
		assertThat(cardProperties.has("lessonId")).isTrue();
		assertThat(cardProperties.path("lessonId").path("description").asString())
				.contains("강습 상세 조회", "CONFIRMED/IN_PROGRESS");
		assertThat(cardSchema.path("required").toString())
				.doesNotContain("\"offerId\"", "\"lessonId\"");
	}

	@Test
	void generatedOpenApiMatchesWholeProjectContract() throws Exception {
		JsonNode openApi = generatedOpenApi();
		Set<String> actualOperations = collectOperations(openApi.path("paths"));

		assertThat(actualOperations).isEqualTo(EXPECTED_OPEN_API_OPERATIONS);
		for (String operationKey : actualOperations) {
			JsonNode operation = findOperation(openApi, operationKey);
			assertThat(operation.path("tags").isEmpty())
					.as(operationKey + " tag")
					.isFalse();
			assertThat(operation.path("summary").asString())
					.as(operationKey + " summary")
					.isNotBlank();
			assertThat(operation.path("description").asString())
					.as(operationKey + " description")
					.isNotBlank();
			assertAuthenticationContract(operationKey, operation);
			assertInjectedParametersAreHidden(operationKey, operation);
			assertErrorResponses(operationKey, operation);
		}

		assertNoContentResponse(openApi, "POST /api/v1/auth/logout");
		assertNoContentResponse(openApi, "PUT /api/v1/fcm-tokens");
		assertNoContentResponse(openApi, "POST /api/v1/fcm-tokens/unregister");
		assertThat(findOperation(openApi, "POST /api/v1/consumer/matching-requests")
				.path("responses").has("201")).isTrue();
		assertThat(findOperation(openApi, "POST /api/v1/consumer/matching-requests")
				.path("responses")
				.path("409")
				.path("content")
				.path("application/json")
				.path("examples")
				.has("MATCHING_REQUEST_ALREADY_EXISTS")).isTrue();
		assertAllLocalReferencesResolve(openApi, openApi);
	}

	private JsonNode generatedOpenApi() throws Exception {
		String json = mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();
		return objectMapper.readTree(json);
	}

	private Set<String> collectOperations(JsonNode paths) {
		Set<String> operations = new TreeSet<>();
		paths.forEachEntry((path, pathItem) -> pathItem.forEachEntry((method, operation) -> {
			if (HTTP_METHODS.contains(method)) {
				operations.add(method.toUpperCase() + " " + path);
			}
		}));
		return operations;
	}

	private Set<String> schemaReferences(JsonNode schemas) {
		Set<String> references = new TreeSet<>();
		schemas.forEach(schema -> references.add(schema.path("$ref").asString()));
		return references;
	}

	private Set<String> textValues(JsonNode values) {
		Set<String> texts = new TreeSet<>();
		values.forEach(value -> texts.add(value.asString()));
		return texts;
	}

	private void assertDiscriminatorMapping(
			JsonNode schemas,
			String schemaName,
			String lessonStatus,
			String expectedSchemaReference
	) {
		assertThat(schemas.path(schemaName).path("discriminator").path("propertyName").asString())
				.isEqualTo("lessonStatus");
		assertThat(schemas.path(schemaName).path("discriminator").path("mapping").path(lessonStatus).asString())
				.isEqualTo(expectedSchemaReference);
	}

	private void assertRecoveryDiscriminatorMapping(
			JsonNode schemas,
			String schemaName,
			String recoveryState,
			String expectedSchemaReference
	) {
		assertThat(schemas.path(schemaName).path("discriminator").path("propertyName").asString())
				.isEqualTo("recoveryState");
		assertThat(schemas.path(schemaName).path("discriminator").path("mapping").path(recoveryState).asString())
				.isEqualTo(expectedSchemaReference);
	}

	private JsonNode findOperation(JsonNode openApi, String operationKey) {
		int separatorIndex = operationKey.indexOf(' ');
		String method = operationKey.substring(0, separatorIndex).toLowerCase();
		String path = operationKey.substring(separatorIndex + 1);
		return openApi.path("paths").path(path).path(method);
	}

	private JsonNode findParameter(JsonNode operation, String parameterName) {
		for (JsonNode parameter : operation.path("parameters")) {
			if (parameterName.equals(parameter.path("name").asString())) {
				return parameter;
			}
		}
		throw new AssertionError("OpenAPI parameter not found: " + parameterName);
	}

	private Set<String> fieldNames(JsonNode objectNode) {
		Set<String> names = new TreeSet<>();
		objectNode.forEachEntry((name, value) -> names.add(name));
		return names;
	}

	private Set<String> notificationTextValues(JsonNode arrayNode) {
		Set<String> values = new TreeSet<>();
		arrayNode.forEach(value -> values.add(value.asString()));
		return values;
	}

	private void assertAuthenticationContract(String operationKey, JsonNode operation) {
		if (PUBLIC_OPEN_API_OPERATIONS.contains(operationKey)) {
			assertThat(operation.has("security"))
					.as(operationKey + " public security")
					.isFalse();
			return;
		}

		assertThat(operation.path("security").toString())
				.as(operationKey + " BearerAuth")
				.contains("BearerAuth");
	}

	private void assertInjectedParametersAreHidden(String operationKey, JsonNode operation) {
		for (JsonNode parameter : operation.path("parameters")) {
			assertThat(parameter.path("name").asString())
					.as(operationKey + " server-injected parameter")
					.isNotEqualTo("currentMember");
		}
	}

	private void assertErrorResponses(String operationKey, JsonNode operation) {
		JsonNode responses = operation.path("responses");
		assertThat(responses.has("500"))
				.as(operationKey + " 500 response")
				.isTrue();

		responses.forEachEntry((responseCode, response) -> {
			if (!responseCode.matches("[45]\\d\\d")) {
				return;
			}
			JsonNode mediaType = response.path("content").path("application/json");
			assertThat(mediaType.isMissingNode())
					.as(operationKey + " " + responseCode + " application/json")
					.isFalse();
			assertThat(mediaType.has("schema"))
					.as(operationKey + " " + responseCode + " schema")
					.isTrue();
			assertThat(mediaType.path("examples").isEmpty())
					.as(operationKey + " " + responseCode + " examples")
					.isFalse();
			mediaType.path("examples").forEachEntry((exampleCode, example) -> {
				JsonNode value = example.path("value");
				assertThat(value.path("code").asString()).isEqualTo(exampleCode);
				assertThat(value.path("success").asBoolean()).isFalse();
				assertThat(value.path("message").asString()).isNotBlank();
				assertThat(value.path("requestId").asString()).isNotBlank();
				assertThat(value.has("data")).isFalse();
				if ("VALIDATION_FAILED".equals(exampleCode)) {
					assertThat(value.path("errors").isObject()).isTrue();
				}
			});
		});
	}

	private void assertNoContentResponse(JsonNode openApi, String operationKey) {
		JsonNode response = findOperation(openApi, operationKey).path("responses").path("204");
		assertThat(response.isMissingNode()).as(operationKey + " 204 response").isFalse();
		assertThat(response.has("content")).as(operationKey + " 204 content").isFalse();
	}

	private void assertAllLocalReferencesResolve(JsonNode openApi, JsonNode node) {
		if (node.isObject()) {
			node.forEachEntry((name, value) -> {
				if ("$ref".equals(name)) {
					assertLocalReferenceResolves(openApi, value.asString());
				} else {
					assertAllLocalReferencesResolve(openApi, value);
				}
			});
			return;
		}
		if (node.isArray()) {
			node.forEach(value -> assertAllLocalReferencesResolve(openApi, value));
		}
	}

	private void assertLocalReferenceResolves(JsonNode openApi, String reference) {
		assertThat(reference).startsWith("#/");
		JsonNode target = openApi;
		for (String rawSegment : reference.substring(2).split("/")) {
			String segment = rawSegment.replace("~1", "/").replace("~0", "~");
			target = target.path(segment);
		}
		assertThat(target.isMissingNode()).as("unresolved OpenAPI reference " + reference).isFalse();
	}

}
