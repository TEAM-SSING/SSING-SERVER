package org.sopt.ssingserver.global.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class SuccessResponseFactoryTest {

    @Test
    void successEntityUsesSuccessCodeHttpStatusAndBody() {
        ResponseEntity<BaseResponse<String>> response = SuccessResponseFactory.success(TestSuccessCode.CREATED, "saved");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().code()).isEqualTo("CREATED");
        assertThat(response.getBody().message()).isEqualTo("created");
        assertThat(response.getBody().data()).isEqualTo("saved");
    }

    @Test
    void noContentEntityDoesNotCarryBody() {
        ResponseEntity<Void> response = SuccessResponseFactory.noContent(TestSuccessCode.NO_CONTENT);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.hasBody()).isFalse();
    }

    @Test
    void baseSuccessRejectsNoContentBody() {
        assertThatThrownBy(() -> BaseResponse.success(TestSuccessCode.NO_CONTENT, "body"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private enum TestSuccessCode implements SuccessCode {
        CREATED(HttpStatus.CREATED, "created"),
        NO_CONTENT(HttpStatus.NO_CONTENT, "deleted");

        private final HttpStatus status;
        private final String message;

        TestSuccessCode(HttpStatus status, String message) {
            this.status = status;
            this.message = message;
        }

        @Override
        public HttpStatus getStatus() {
            return status;
        }

        @Override
        public String getCode() {
            return name();
        }

        @Override
        public String getMessage() {
            return message;
        }
    }
}
