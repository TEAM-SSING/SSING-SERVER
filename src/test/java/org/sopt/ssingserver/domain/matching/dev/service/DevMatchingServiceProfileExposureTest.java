package org.sopt.ssingserver.domain.matching.dev.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.auth.dev.repository.DevPersonaRepository;
import org.sopt.ssingserver.domain.lesson.repository.LessonRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingOfferRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestGroupItemRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestParticipantRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestRepository;
import org.sopt.ssingserver.domain.matching.service.ConsumerMatchingProgressService;
import org.sopt.ssingserver.domain.matching.service.InstructorMatchingOfferService;
import org.sopt.ssingserver.domain.matching.service.MatchingStatusResolver;
import org.sopt.ssingserver.domain.payment.repository.MatchingOfferPriceSnapshotRepository;
import org.sopt.ssingserver.domain.payment.repository.MatchingRequestPaymentRepository;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DevMatchingServiceProfileExposureTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(MatchingRequestRepository.class, () -> mock(MatchingRequestRepository.class))
            .withBean(MatchingRequestGroupItemRepository.class, () -> mock(MatchingRequestGroupItemRepository.class))
            .withBean(MatchingOfferRepository.class, () -> mock(MatchingOfferRepository.class))
            .withBean(
                    MatchingOfferPriceSnapshotRepository.class,
                    () -> mock(MatchingOfferPriceSnapshotRepository.class)
            )
            .withBean(MatchingRequestPaymentRepository.class, () -> mock(MatchingRequestPaymentRepository.class))
            .withBean(
                    MatchingRequestParticipantRepository.class,
                    () -> mock(MatchingRequestParticipantRepository.class)
            )
            .withBean(LessonRepository.class, () -> mock(LessonRepository.class))
            .withBean(DevPersonaRepository.class, () -> mock(DevPersonaRepository.class))
            .withBean(
                    InstructorMatchingOfferService.class,
                    () -> mock(InstructorMatchingOfferService.class)
            )
            .withBean(
                    ConsumerMatchingProgressService.class,
                    () -> mock(ConsumerMatchingProgressService.class)
            )
            .withBean(MatchingStatusResolver.class, MatchingStatusResolver::new)
            .withBean(Clock.class, Clock::systemUTC)
            .withUserConfiguration(
                    DevMatchingQueryService.class,
                    DevMatchingActionService.class,
                    DevMatchingActionPolicy.class,
                    DevMatchingActionPreviewFactory.class,
                    DevMatchingStateTokenFactory.class
            );

    @Test
    void 조회_Service와_helper는_local과_dev에서_플래그와_무관하게_등록한다() {
        assertReadExposure("local", true);
        assertReadExposure("dev", true);
        assertReadExposure("prod", false);
    }

    @Test
    void 상태변경_Service는_local과_dev에서_기능플래그_true일_때만_등록한다() {
        assertActionExposure("local", null, false);
        assertActionExposure("local", false, false);
        assertActionExposure("local", true, true);
        assertActionExposure("dev", true, true);
        assertActionExposure("prod", true, false);
    }

    private void assertReadExposure(String profile, boolean expected) {
        contextRunner.withPropertyValues("spring.profiles.active=" + profile)
                .run(context -> {
                    assertThat(context.containsBean("devMatchingQueryService")).isEqualTo(expected);
                    assertThat(context.containsBean("devMatchingActionPolicy")).isEqualTo(expected);
                    assertThat(context.containsBean("devMatchingActionPreviewFactory")).isEqualTo(expected);
                    assertThat(context.containsBean("devMatchingStateTokenFactory")).isEqualTo(expected);
                });
    }

    private void assertActionExposure(String profile, Boolean enabled, boolean expected) {
        ApplicationContextRunner runner = contextRunner
                .withPropertyValues("spring.profiles.active=" + profile);
        if (enabled != null) {
            runner = runner.withPropertyValues("ssing.dev-matching-actions.enabled=" + enabled);
        }
        runner.run(context ->
                assertThat(context.containsBean("devMatchingActionService")).isEqualTo(expected)
        );
    }
}
