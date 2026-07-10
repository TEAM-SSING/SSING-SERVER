package org.sopt.ssingserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
		String json = mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();
		JsonNode openApi = objectMapper.readTree(json);

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
	}

}
