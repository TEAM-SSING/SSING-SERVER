package org.sopt.ssingserver.domain.instructor.dev.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.sopt.ssingserver.domain.instructor.dev.dto.request.DevInstructorConfigurationRequest;
import org.sopt.ssingserver.domain.instructor.entity.InstructorMatchingSetting;
import org.sopt.ssingserver.domain.instructor.entity.InstructorPricePolicy;
import org.sopt.ssingserver.domain.instructor.entity.InstructorProfile;
import org.sopt.ssingserver.domain.instructor.enums.InstructorCertificateType;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.global.error.BusinessValidationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile({"local", "dev"})
@ConditionalOnProperty(name = "ssing.dev-instructor-actions.enabled", havingValue = "true")
@Component
class DevInstructorConfigurationPolicy {

    static final int MIN_BASE_PRICE = 50_000;
    static final int MAX_BASE_PRICE = 200_000;
    static final int MIN_ADDITIONAL_PRICE = 0;
    static final int MAX_ADDITIONAL_PRICE = 50_000;
    static final int PRICE_STEP = 5_000;
    private static final Set<Integer> ALLOWED_DURATIONS = Set.of(120, 180, 240);

    void validate(DevInstructorConfigurationRequest configuration) {
        if (configuration == null) {
            throw BusinessValidationException.of("configuration", "승인 또는 설정 저장에는 선택값이 필요합니다.");
        }
        if (configuration.resortCode() == null || configuration.resortCode().isBlank()) {
            throw BusinessValidationException.of("resortCode", "리조트를 선택해 주세요.");
        }
        if (configuration.sport() == null) {
            throw BusinessValidationException.of("sport", "종목을 선택해 주세요.");
        }
        validateDistinctList("lessonLevels", configuration.lessonLevels(), "레벨을 하나 이상 중복 없이 선택해 주세요.");
        validateDistinctList(
                "availableDurationMinutes",
                configuration.availableDurationMinutes(),
                "120분, 180분, 240분 중 하나 이상을 중복 없이 선택해 주세요."
        );
        if (!ALLOWED_DURATIONS.containsAll(configuration.availableDurationMinutes())) {
            throw BusinessValidationException.of(
                    "availableDurationMinutes",
                    "가능 시간은 120분, 180분, 240분만 선택할 수 있습니다."
            );
        }
        if (configuration.maxHeadcount() < 1 || configuration.maxHeadcount() > 5) {
            throw BusinessValidationException.of("maxHeadcount", "최대 인원은 1명부터 5명까지 선택할 수 있습니다.");
        }
        if (!isValidPrice(configuration.basePriceAmount(), MIN_BASE_PRICE, MAX_BASE_PRICE)) {
            throw BusinessValidationException.of(
                    "basePriceAmount",
                    "기본 가격은 50000원부터 200000원까지 5000원 단위로 선택해 주세요."
            );
        }
        if (!isValidPrice(
                configuration.additionalPersonPriceAmount(),
                MIN_ADDITIONAL_PRICE,
                MAX_ADDITIONAL_PRICE
        )) {
            throw BusinessValidationException.of(
                    "additionalPersonPriceAmount",
                    "추가 인원 가격은 0원부터 50000원까지 5000원 단위로 선택해 주세요."
            );
        }
    }

    boolean isConfigurationComplete(
            InstructorProfile profile,
            InstructorMatchingSetting setting,
            List<InstructorPricePolicy> activePricePolicies
    ) {
        if (profile == null || setting == null || profile.getResort() == null) {
            return false;
        }
        if (!setting.isEquipmentReady()
                || setting.getMaxHeadcount() < 1
                || setting.getMaxHeadcount() > 5
                || setting.getSport() == null
                || setting.getLessonLevels().isEmpty()
                || setting.getAvailableDurationMinutes().isEmpty()
                || !ALLOWED_DURATIONS.containsAll(setting.getAvailableDurationMinutes())
                || !profile.hasCertificateFor(setting.getSport())) {
            return false;
        }
        if (activePricePolicies.size() != 1) {
            return false;
        }
        InstructorPricePolicy pricePolicy = activePricePolicies.getFirst();
        return isValidPrice(pricePolicy.getBasePriceAmount(), MIN_BASE_PRICE, MAX_BASE_PRICE)
                && isValidPrice(
                        pricePolicy.getAdditionalPersonPriceAmount(),
                        MIN_ADDITIONAL_PRICE,
                        MAX_ADDITIONAL_PRICE
                );
    }

    InstructorCertificateType testCertificateFor(Sport sport) {
        return switch (sport) {
            case SKI -> InstructorCertificateType.KSIA_SKI_LEVEL_1;
            case SNOWBOARD -> InstructorCertificateType.KSIA_SNOWBOARD_LEVEL_1;
        };
    }

    private void validateDistinctList(String field, List<?> values, String message) {
        if (values == null || values.isEmpty() || values.stream().anyMatch(value -> value == null)) {
            throw BusinessValidationException.of(field, message);
        }
        if (new HashSet<>(values).size() != values.size()) {
            throw BusinessValidationException.of(field, message);
        }
    }

    private boolean isValidPrice(int amount, int min, int max) {
        return amount >= min && amount <= max && amount % PRICE_STEP == 0;
    }
}
