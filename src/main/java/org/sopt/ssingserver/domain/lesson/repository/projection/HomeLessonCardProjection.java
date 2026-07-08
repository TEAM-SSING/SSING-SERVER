package org.sopt.ssingserver.domain.lesson.repository.projection;

import java.time.Instant;
import org.sopt.ssingserver.domain.lesson.enums.LessonStatus;

public interface HomeLessonCardProjection {

    Long getLessonId();

    LessonStatus getLessonStatus();

    Instant getScheduledAt();

    String getRequesterNickname();

    int getTotalHeadcount();

    String getResortCode();

    String getResortDisplayName();
}
