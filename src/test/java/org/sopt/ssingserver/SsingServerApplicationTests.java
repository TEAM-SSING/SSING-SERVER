package org.sopt.ssingserver;

import org.junit.jupiter.api.Test;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;

@ActiveProfiles("test")
@SpringBootTest
class SsingServerApplicationTests {

	@MockitoBean
	private OAuthAccountRepository oauthAccountRepository;

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

}
