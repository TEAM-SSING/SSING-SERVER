package org.sopt.ssingserver.domain.matching.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@ConditionalOnProperty(name = "ssing.matching.search-scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class MatchingSearchSchedulerConfig {

    public static final String MATCHING_SEARCH_TASK_SCHEDULER = "matchingSearchTaskScheduler";

    @Bean(name = MATCHING_SEARCH_TASK_SCHEDULER)
    public TaskScheduler matchingSearchTaskScheduler() {
        // DB 매칭 탐색이 길어져도 WebSocket heartbeat가 밀리지 않도록 실행 스레드를 분리한다.
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("matching-search-");
        return scheduler;
    }
}
