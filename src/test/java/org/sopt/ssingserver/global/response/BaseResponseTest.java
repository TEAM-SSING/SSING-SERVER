package org.sopt.ssingserver.global.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.global.error.CommonErrorCode;

class BaseResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void errorResponseSerializesRequestIdField() throws Exception {
        BaseResponse<Void> response = BaseResponse.error(CommonErrorCode.NOT_FOUND, "req-123");

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"requestId\":\"req-123\"");
        assertThat(json).doesNotContain("traceId");
    }
}
