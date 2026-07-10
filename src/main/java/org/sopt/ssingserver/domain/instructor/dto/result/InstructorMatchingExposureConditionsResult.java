package org.sopt.ssingserver.domain.instructor.dto.result;

import java.util.List;
import org.sopt.ssingserver.domain.instructor.entity.InstructorProfile;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.domain.resort.entity.Resort;

public record InstructorMatchingExposureConditionsResult(
        ResortResult resort,
        List<Sport> availableSports
) {

    public static InstructorMatchingExposureConditionsResult from(
            InstructorProfile instructorProfile
    ) {
        // Service 트랜잭션 안에서 LAZY 연관값을 화면용 Result로 복사해 Controller까지 영속성 객체가 새지 않게 한다.
        Resort resort = instructorProfile.getResort();
        return new InstructorMatchingExposureConditionsResult(
                new ResortResult(resort.getCode(), resort.getDisplayName()),
                instructorProfile.getAvailableSports().stream().toList()
        );
    }

    public record ResortResult(
            String code,
            String displayName
    ) {
    }
}
