package org.sopt.ssingserver.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    ObjectMapper objectMapper() {
        // Security filter 단계에서도 컨트롤러 응답과 같은 JSON 직렬화 규칙을 사용한다.
        return new ObjectMapper().findAndRegisterModules();
    }
}
