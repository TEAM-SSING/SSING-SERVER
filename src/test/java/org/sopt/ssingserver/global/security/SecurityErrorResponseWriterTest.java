package org.sopt.ssingserver.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.sopt.ssingserver.global.error.ErrorResponseFactory;
import org.sopt.ssingserver.global.monitoring.ClientErrorTrackingPolicy;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

class SecurityErrorResponseWriterTest {

    @Test
    void Security_4xx_응답은_예상된_클라이언트_오류로_표시한다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        SecurityErrorResponseWriter writer = new SecurityErrorResponseWriter(
                new ErrorResponseFactory(),
                new ObjectMapper()
        );

        writer.write(request, response, CommonErrorCode.UNAUTHENTICATED);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(ClientErrorTrackingPolicy.isDeclared(request)).isTrue();
    }
}
