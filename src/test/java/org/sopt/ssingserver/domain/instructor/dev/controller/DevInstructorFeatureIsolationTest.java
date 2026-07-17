package org.sopt.ssingserver.domain.instructor.dev.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.swagger.v3.oas.annotations.Hidden;
import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.instructor.dev.service.DevInstructorActionService;
import org.sopt.ssingserver.domain.instructor.dev.service.DevInstructorQueryService;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

class DevInstructorFeatureIsolationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withUserConfiguration(DevInstructorController.class)
            .withBean(DevInstructorQueryService.class, () -> mock(DevInstructorQueryService.class))
            .withBean(DevInstructorActionService.class, () -> mock(DevInstructorActionService.class));

    @Test
    void 상태변경_API는_Swagger_Try_it_out에서_숨긴다() {
        assertThat(DevInstructorController.class).hasAnnotation(Hidden.class);
    }

    @Test
    void local에서도_기능플래그가_없거나_false이면_컨트롤러가_없다() {
        contextRunner
                .withPropertyValues("spring.profiles.active=local")
                .run(context -> assertThat(context).doesNotHaveBean(DevInstructorController.class));

        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=local",
                        "ssing.dev-instructor-actions.enabled=false"
                )
                .run(context -> assertThat(context).doesNotHaveBean(DevInstructorController.class));
    }

    @Test
    void local과_dev에서만_기능플래그_true일_때_컨트롤러가_생긴다() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=local",
                        "ssing.dev-instructor-actions.enabled=true"
                )
                .run(context -> assertThat(context).hasSingleBean(DevInstructorController.class));

        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=dev",
                        "ssing.dev-instructor-actions.enabled=true"
                )
                .run(context -> assertThat(context).hasSingleBean(DevInstructorController.class));
    }

    @Test
    void prod에서는_기능플래그가_true여도_컨트롤러가_없다() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "ssing.dev-instructor-actions.enabled=true"
                )
                .run(context -> assertThat(context).doesNotHaveBean(DevInstructorController.class));
    }
}
