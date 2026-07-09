package org.sopt.ssingserver.domain.matching.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.matching.service.MatchingSearchScheduler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class MatchingSearchSchedulerConfigTest {

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
}
