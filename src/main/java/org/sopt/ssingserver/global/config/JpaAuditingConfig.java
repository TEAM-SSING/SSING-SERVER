package org.sopt.ssingserver.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
// 기본 테스트는 DB 없이 실행하므로 JPA auditing 설정은 test profile에서 제외한다.
@Profile("!test")
@EnableJpaAuditing
public class JpaAuditingConfig {
}
