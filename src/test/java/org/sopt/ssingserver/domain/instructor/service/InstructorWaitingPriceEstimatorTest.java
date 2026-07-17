package org.sopt.ssingserver.domain.instructor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class InstructorWaitingPriceEstimatorTest {

    private final InstructorWaitingPriceEstimator estimator = new InstructorWaitingPriceEstimator();

    @Test
    void 선택한_시간과_최대인원의_중간값으로_예상가격을_계산한다() {
        int estimatedPrice = estimator.estimate(
                60_000,
                20_000,
                List.of(120, 180),
                3
        );

        assertThat(estimatedPrice).isEqualTo(87_500);
    }

    @Test
    void 시간_입력_순서가_달라도_최솟값과_최댓값의_중간값을_사용한다() {
        int ascending = estimator.estimate(60_000, 20_000, List.of(120, 180, 300), 3);
        int unordered = estimator.estimate(60_000, 20_000, List.of(300, 120, 180), 3);

        assertThat(unordered).isEqualTo(ascending).isEqualTo(122_500);
    }

    @Test
    void 최대인원이_1명이면_예상인원을_1명으로_유지한다() {
        int estimatedPrice = estimator.estimate(60_000, 20_000, List.of(120), 1);

        assertThat(estimatedPrice).isEqualTo(60_000);
    }

    @Test
    void 세시간에서_네시간과_최대_다섯명은_3점5시간과_2점5명으로_계산한다() {
        int estimatedPrice = estimator.estimate(60_000, 20_000, List.of(180, 240), 5);

        assertThat(estimatedPrice).isEqualTo(157_500);
    }

    @Test
    void 전체_계산이_끝난_가격을_500원_단위로_HALF_UP_반올림한다() {
        int estimatedPrice = estimator.estimate(60_250, 0, List.of(120), 1);

        assertThat(estimatedPrice).isEqualTo(60_500);
    }

    @Test
    void 오백원_단위의_절반보다_작은_가격은_아래로_반올림한다() {
        int estimatedPrice = estimator.estimate(60_249, 0, List.of(120), 1);

        assertThat(estimatedPrice).isEqualTo(60_000);
    }

    @Test
    void 중간금액을_먼저_반올림하지_않고_시간환산까지_끝난_뒤_한번만_반올림한다() {
        int estimatedPrice = estimator.estimate(60_000, 500, List.of(180), 3);

        assertThat(estimatedPrice).isEqualTo(90_500);
    }

    @Test
    void 가능한_시간이_비어있으면_임의의_예상가격을_만들지_않는다() {
        assertThatThrownBy(() -> estimator.estimate(60_000, 20_000, List.of(), 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("availableDurationMinutes must not be empty.");
    }
}
