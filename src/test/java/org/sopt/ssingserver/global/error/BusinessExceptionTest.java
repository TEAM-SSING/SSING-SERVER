package org.sopt.ssingserver.global.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class BusinessExceptionTest {

    @Test
    void preservesCauseWithoutChangingErrorCodeMessage() {
        IOException cause = new IOException("downstream failed");

        BusinessException exception = new BusinessException(CommonErrorCode.INTERNAL_ERROR, cause);

        assertThat(exception.getErrorCode()).isSameAs(CommonErrorCode.INTERNAL_ERROR);
        assertThat(exception).hasMessage(CommonErrorCode.INTERNAL_ERROR.getMessage());
        assertThat(exception).hasCause(cause);
    }
}
