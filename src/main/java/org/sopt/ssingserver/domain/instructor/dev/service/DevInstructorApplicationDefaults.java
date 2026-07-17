package org.sopt.ssingserver.domain.instructor.dev.service;

import java.time.LocalDate;
import org.sopt.ssingserver.domain.instructor.dev.dto.response.DevInstructorApplicationDefaultsResponse;
import org.sopt.ssingserver.domain.member.enums.Gender;

final class DevInstructorApplicationDefaults {

    static final String PHONE = "010-0000-0000";
    static final Gender GENDER = Gender.MALE;
    static final LocalDate BIRTH_DATE = LocalDate.of(2000, 1, 1);
    static final String INTRO = "개발용 강사 프로필입니다.";
    static final LocalDate CAREER_START_DATE = LocalDate.of(2020, 1, 1);

    private DevInstructorApplicationDefaults() {
    }

    static DevInstructorApplicationDefaultsResponse response() {
        return new DevInstructorApplicationDefaultsResponse(
                "카카오 닉네임 사용",
                PHONE,
                GENDER,
                BIRTH_DATE,
                INTRO,
                CAREER_START_DATE
        );
    }
}
