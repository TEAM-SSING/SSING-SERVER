package org.sopt.ssingserver.domain.instructor.dto.result;

import java.util.List;
import java.util.Optional;
import org.sopt.ssingserver.domain.instructor.entity.InstructorMatchingSetting;
import org.sopt.ssingserver.domain.instructor.entity.InstructorProfile;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.domain.resort.entity.Resort;

public record InstructorMatchingExposureConditionsResult(
        ResortResult resort,
        List<Sport> availableSports,
        List<Integer> durationOptions,
        CurrentSettingResult currentSetting
) {

    public static InstructorMatchingExposureConditionsResult from(
            InstructorProfile instructorProfile,
            Optional<InstructorMatchingSetting> currentSetting,
            List<Integer> durationOptions
    ) {
        // Service 트랜잭션 안에서 LAZY 연관값을 화면용 Result로 복사해 Controller까지 영속성 객체가 새지 않게 한다.
        Resort resort = instructorProfile.getResort();
        return new InstructorMatchingExposureConditionsResult(
                new ResortResult(resort.getCode(), resort.getDisplayName()),
                instructorProfile.getAvailableSports().stream().toList(),
                List.copyOf(durationOptions),
                currentSetting.map(CurrentSettingResult::from).orElse(null)
        );
    }

    public record ResortResult(
            String code,
            String displayName
    ) {
    }

    public record CurrentSettingResult(
            Sport sport,
            List<LessonLevel> lessonLevels,
            int maxHeadcount,
            boolean equipmentReady,
            List<Integer> availableDurationMinutes,
            boolean isExposed
    ) {

        private static CurrentSettingResult from(InstructorMatchingSetting setting) {
            // Set으로 저장된 선택값은 응답 순서를 고정해 앱 초기값과 테스트 결과가 흔들리지 않게 한다.
            return new CurrentSettingResult(
                    setting.getSport(),
                    setting.getLessonLevels().stream().sorted().toList(),
                    setting.getMaxHeadcount(),
                    setting.isEquipmentReady(),
                    setting.getAvailableDurationMinutes().stream().sorted().toList(),
                    setting.isExposed()
            );
        }
    }
}
