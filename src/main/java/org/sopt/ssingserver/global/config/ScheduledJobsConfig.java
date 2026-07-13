package org.sopt.ssingserver.global.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// 운영 기본 동작은 유지하고, 통합 테스트에서는 시간 기반 DB 변경을 한 번에 차단한다.
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "ssing.scheduled-jobs.enabled", havingValue = "true", matchIfMissing = true)
public class ScheduledJobsConfig {
}
