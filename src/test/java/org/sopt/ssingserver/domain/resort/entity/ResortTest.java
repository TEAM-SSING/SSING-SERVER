package org.sopt.ssingserver.domain.resort.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ResortTest {

    @Test
    void create는_0원_패찰비를_허용한다() {
        Resort resort = Resort.create("HIGH1", "하이원리조트", "하이원", 0);

        assertThat(resort.getPassFeeAmount()).isZero();
    }

    @Test
    void create는_음수_패찰비를_허용하지_않는다() {
        assertThatThrownBy(() -> Resort.create("HIGH1", "하이원리조트", "하이원", -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("passFeeAmount must be non-negative.");
    }
}
