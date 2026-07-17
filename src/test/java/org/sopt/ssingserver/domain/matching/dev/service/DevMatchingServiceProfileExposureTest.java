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
            .withBean(MatchingStatusResolver.class, MatchingStatusResolver::new)
            .withBean(Clock.class, Clock::systemUTC)
            .withUserConfiguration(
                    DevMatchingQueryService.class,
                    DevMatchingActionPreviewFactory.class,
                    DevMatchingStateTokenFactory.class
            );

    @Test
    void local과_dev_profile에서만_매칭_개발도구_Service와_helper를_등록한다() {
        assertExposure("local", true);
        assertExposure("dev", true);
        assertExposure("prod", false);
    }

    private void assertExposure(String profile, boolean expected) {
        contextRunner.withPropertyValues("spring.profiles.active=" + profile)
                .run(context -> {
                    assertThat(context.containsBean("devMatchingQueryService")).isEqualTo(expected);
                    assertThat(context.containsBean("devMatchingActionPreviewFactory")).isEqualTo(expected);
                    assertThat(context.containsBean("devMatchingStateTokenFactory")).isEqualTo(expected);
                });
    }
}
