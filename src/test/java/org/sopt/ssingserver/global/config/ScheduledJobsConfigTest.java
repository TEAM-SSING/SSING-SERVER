package org.sopt.ssingserver.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.config.TaskManagementConfigUtils;

class ScheduledJobsConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ScheduledJobsConfig.class);

    @Test
    void 별도_설정이_없으면_scheduled_작업을_활성화한다() {
        contextRunner.run(context -> assertThat(context.containsBean(
                TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME
        )).isTrue());
    }

    @Test
    void scheduled_jobs를_끄면_예약_작업_후처리기_자체를_등록하지_않는다() {
        contextRunner
                .withPropertyValues("ssing.scheduled-jobs.enabled=false")
                .run(context -> assertThat(context.containsBean(
                        TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME
                )).isFalse());
    }
}
