package org.sopt.ssingserver.domain.matching.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.matching.service.MatchingOfferExpirationTriggerService;
import org.sopt.ssingserver.domain.matching.service.MatchingSearchScheduler;
import org.sopt.ssingserver.domain.matching.service.MatchingSearchTriggerService;
import org.sopt.ssingserver.global.config.ScheduledJobsConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.config.TaskManagementConfigUtils;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class MatchingSearchSchedulerConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    ScheduledJobsConfig.class,
                    MatchingSearchSchedulerConfig.class,
                    MatchingSearchScheduler.class
            )
            .withBean(
                    MatchingOfferExpirationTriggerService.class,
                    () -> mock(MatchingOfferExpirationTriggerService.class)
            )
            .withBean(
                    MatchingSearchTriggerService.class,
                    () -> mock(MatchingSearchTriggerService.class)
            )
            .withPropertyValues(
                    "ssing.scheduled-jobs.enabled=false",
                    "ssing.matching.search-scheduler.enabled=true"
            );

    @Test
    void 매칭_재탐색은_WebSocket_heartbeat와_분리된_scheduler를_명시한다() throws Exception {
        Method scheduledMethod = MatchingSearchScheduler.class.getDeclaredMethod("runScheduledSearch");
        Scheduled scheduled = scheduledMethod.getAnnotation(Scheduled.class);

        assertThat(scheduled.scheduler())
                .isEqualTo(MatchingSearchSchedulerConfig.MATCHING_SEARCH_TASK_SCHEDULER);
    }

    @Test
    void matchingSearchTaskScheduler는_매칭_재탐색_전용_이름을_사용한다() {
        ThreadPoolTaskScheduler scheduler = (ThreadPoolTaskScheduler) new MatchingSearchSchedulerConfig()
                .matchingSearchTaskScheduler();

        assertThat(scheduler.getThreadNamePrefix()).isEqualTo("matching-search-");
    }

    @Test
    void 매칭_재탐색을_끄면_전용_scheduler도_생성하지_않는다() {
        ConditionalOnProperty condition = MatchingSearchSchedulerConfig.class
                .getAnnotation(ConditionalOnProperty.class);

        assertThat(condition.name()).containsExactly("ssing.matching.search-scheduler.enabled");
        assertThat(condition.havingValue()).isEqualTo("true");
        assertThat(condition.matchIfMissing()).isTrue();
    }

    @Test
    void integration_test_설정은_예약_작업_자동_등록을_끄고_매칭_scheduler_빈은_유지한다() {
        contextRunner.run(context -> {
            assertThat(context.containsBean(
                    TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME
            )).isFalse();
            assertThat(context.getBeansOfType(MatchingSearchScheduler.class)).hasSize(1);
            assertThat(context.containsBean(
                    MatchingSearchSchedulerConfig.MATCHING_SEARCH_TASK_SCHEDULER
            )).isTrue();
        });
    }
}
