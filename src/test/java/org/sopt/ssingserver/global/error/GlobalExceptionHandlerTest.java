package org.sopt.ssingserver.global.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.global.logging.RequestIdFilter;
import org.sopt.ssingserver.global.response.BaseResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class GlobalExceptionHandlerTest {

    @Test
    void 비즈니스_필드_검증_실패를_VALIDATION_FAILED_응답으로_변환한다() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(new ErrorResponseFactory());
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/v1/instructors/me/matching-exposure");
        request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "req-90");
        BusinessValidationException exception = BusinessValidationException.of(
                "sport",
                "보유 자격증으로 선택할 수 없는 종목입니다."
        );

        ResponseEntity<BaseResponse<Void>> response =
                handler.handleBusinessValidationException(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_FAILED");
        assertThat(response.getBody().errors())
                .containsEntry("sport", "보유 자격증으로 선택할 수 없는 종목입니다.");
        assertThat(response.getBody().requestId()).isEqualTo("req-90");
    }
}
