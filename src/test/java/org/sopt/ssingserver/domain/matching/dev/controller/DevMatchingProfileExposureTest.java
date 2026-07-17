package org.sopt.ssingserver.domain.matching.dev.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.swagger.v3.oas.annotations.Hidden;
import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.matching.dev.service.DevMatchingActionService;
import org.sopt.ssingserver.domain.matching.dev.service.DevMatchingQueryService;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

class DevMatchingProfileExposureTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withBean(DevMatchingQueryService.class, () -> mock(DevMatchingQueryService.class))
            .withBean(DevMatchingActionService.class, () -> mock(DevMatchingActionService.class))
            .withUserConfiguration(
                    DevMatchingController.class,
                    DevMatchingActionController.class,
                    DevMatchingConsoleController.class
            );

    @Test
    void 상태변경_Controller는_Swagger_Try_it_out에서_숨긴다() {
        assertThat(DevMatchingActionController.class).hasAnnotation(Hidden.class);
    }

    @Test
    void 조회_Controller는_local과_dev에서_플래그와_무관하게_등록한다() {
        assertQueryExposure("local", true);
        assertQueryExposure("dev", true);
        assertQueryExposure("prod", false);
    }

    @Test
    void 상태변경_Controller는_local과_dev에서_기능플래그_true일_때만_등록한다() {
        assertActionExposure("local", null, false);
        assertActionExposure("local", false, false);
        assertActionExposure("local", true, true);
        assertActionExposure("dev", true, true);
        assertActionExposure("prod", true, false);
    }

    private void assertQueryExposure(String profile, boolean expected) {
        contextRunner.withPropertyValues("spring.profiles.active=" + profile)
                .run(context -> {
                    assertThat(context.containsBean("devMatchingController")).isEqualTo(expected);
                    assertThat(context.containsBean("devMatchingConsoleController")).isEqualTo(expected);
                });
    }

    private void assertActionExposure(String profile, Boolean enabled, boolean expected) {
        WebApplicationContextRunner runner = contextRunner
                .withPropertyValues("spring.profiles.active=" + profile);
        if (enabled != null) {
            runner = runner.withPropertyValues("ssing.dev-matching-actions.enabled=" + enabled);
        }
        runner.run(context ->
                assertThat(context.containsBean("devMatchingActionController")).isEqualTo(expected)
        );
    }
}
