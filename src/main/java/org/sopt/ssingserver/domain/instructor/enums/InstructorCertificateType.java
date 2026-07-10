package org.sopt.ssingserver.domain.instructor.enums;

public enum InstructorCertificateType {
    KSIA_SKI_LEVEL_1(Sport.SKI),
    KSIA_SKI_LEVEL_2(Sport.SKI),
    KSIA_SKI_LEVEL_3(Sport.SKI),
    KSIA_SNOWBOARD_LEVEL_1(Sport.SNOWBOARD),
    KSIA_SNOWBOARD_LEVEL_2(Sport.SNOWBOARD),
    KSIA_SNOWBOARD_LEVEL_3(Sport.SNOWBOARD),
    SBAK_SKI_TEACHING_1(Sport.SKI),
    SBAK_SKI_TEACHING_2(Sport.SKI),
    SBAK_SKI_TEACHING_3(Sport.SKI),
    SBAK_SNOWBOARD_TEACHING_1(Sport.SNOWBOARD),
    SBAK_SNOWBOARD_TEACHING_2(Sport.SNOWBOARD),
    SBAK_SNOWBOARD_TEACHING_3(Sport.SNOWBOARD);

    private final Sport sport;

    InstructorCertificateType(Sport sport) {
        this.sport = sport;
    }

    public Sport sport() {
        return sport;
    }
}
