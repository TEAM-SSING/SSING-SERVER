package org.sopt.ssingserver.domain.matching.dev.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.matching.dev.service.DevMatchingQueryService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DevMatchingProfileExposureTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(DevMatchingQueryService.class, () -> mock(DevMatchingQueryService.class))
            .withUserConfiguration(DevMatchingController.class, DevMatchingConsoleController.class);

    @Test
    void local과_dev_profile에서만_매칭_개발도구_Controller를_등록한다() {
        assertExposure("local", true);
        assertExposure("dev", true);
        assertExposure("prod", false);
    }

    private void assertExposure(String profile, boolean expected) {
        contextRunner.withPropertyValues("spring.profiles.active=" + profile)
                .run(context -> {
                    assertThat(context.containsBean("devMatchingController")).isEqualTo(expected);
                    assertThat(context.containsBean("devMatchingConsoleController")).isEqualTo(expected);
                });
    }
}
