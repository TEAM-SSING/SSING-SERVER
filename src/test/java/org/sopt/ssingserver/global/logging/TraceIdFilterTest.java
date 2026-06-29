package org.sopt.ssingserver.global.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceIdFilterTest {

    @Test
    void reusesValidRequestIdAndExposesItThroughResponseRequestAndMdc() throws Exception {
        TraceIdFilter filter = new TraceIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String requestId = "req-ABC_123.9";
        AtomicReference<String> mdcValueInsideChain = new AtomicReference<>();

        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, requestId);

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                mdcValueInsideChain.set(MDC.get(TraceIdFilter.REQUEST_ID_MDC_KEY)));

        assertThat(response.getHeader(TraceIdFilter.TRACE_ID_HEADER)).isEqualTo(requestId);
        assertThat(request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE)).isEqualTo(requestId);
        assertThat(mdcValueInsideChain.get()).isEqualTo(requestId);
        assertThat(MDC.get(TraceIdFilter.REQUEST_ID_MDC_KEY)).isNull();
    }

    @Test
    void regeneratesInvalidRequestId() throws Exception {
        TraceIdFilter filter = new TraceIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String invalidRequestId = "bad request id with spaces";

        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, invalidRequestId);

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
        });

        String generatedRequestId = response.getHeader(TraceIdFilter.TRACE_ID_HEADER);
        assertThat(generatedRequestId).isNotEqualTo(invalidRequestId);
        assertThat(UUID.fromString(generatedRequestId)).isNotNull();
    }
}
