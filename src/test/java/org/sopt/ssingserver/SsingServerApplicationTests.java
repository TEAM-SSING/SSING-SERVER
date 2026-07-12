package org.sopt.ssingserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
@SpringBootTest(properties = "springdoc.api-docs.enabled=true")
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
			"GET /api/v1/consumer/home",
			"GET /api/v1/instructor/home",
			"GET /api/v1/instructor/matching-exposure",
			"PUT /api/v1/instructor/matching-exposure",
			"POST /api/v1/instructor/matching-exposure/cancellation",
			"POST /api/v1/consumer/matching-requests",
			"GET /api/v1/consumer/matching-requests/{matchingRequestId}",
			"POST /api/v1/consumer/matching-requests/{matchingRequestId}/cancellation",
			"PATCH /api/v1/consumer/matching-requests/{matchingRequestId}/confirmation",
			"POST /api/v1/consumer/matching-requests/{matchingRequestId}/payment",
			"GET /api/v1/instructor/matching-offers",
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

	private JsonNode findOperation(JsonNode openApi, String operationKey) {
		int separatorIndex = operationKey.indexOf(' ');
		String method = operationKey.substring(0, separatorIndex).toLowerCase();
		String path = operationKey.substring(separatorIndex + 1);
		return openApi.path("paths").path(path).path(method);
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
