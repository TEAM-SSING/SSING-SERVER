package org.sopt.ssingserver.global.swagger.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.sopt.ssingserver.global.error.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.HandlerMethod;

class ErrorCodeResolverTest {

    private final ErrorCodeResolver errorCodeResolver = new ErrorCodeResolver();

    @Test
    void ApiDocs_interface에_선언한_오류_코드를_구현_메서드에서_찾는다() throws Exception {
        HandlerMethod handlerMethod = handlerMethod(InterfaceAnnotatedController.class, "handle");

        List<ErrorCode> errorCodes = errorCodeResolver.resolve(handlerMethod);

        assertThat(errorCodes).containsExactly(
                CommonErrorCode.VALIDATION_FAILED,
                CommonErrorCode.INTERNAL_ERROR
        );
    }

    @Test
    void ErrorCode를_구현해도_enum이_아니면_거부한다() throws Exception {
        HandlerMethod handlerMethod = handlerMethod(NonEnumController.class, "handle");

        assertThatThrownBy(() -> errorCodeResolver.resolve(handlerMethod))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("enum");
    }

    @Test
    void 존재하지_않는_enum_상수명은_거부한다() throws Exception {
        HandlerMethod handlerMethod = handlerMethod(UnknownConstantController.class, "handle");

        assertThatThrownBy(() -> errorCodeResolver.resolve(handlerMethod))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UNKNOWN_ERROR");
    }

    @Test
    void 서로_다른_ErrorCode가_같은_code를_사용하면_거부한다() throws Exception {
        HandlerMethod handlerMethod = handlerMethod(DuplicateCodeController.class, "handle");

        assertThatThrownBy(() -> errorCodeResolver.resolve(handlerMethod))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DUPLICATE_CODE");
    }

    @Test
    void 같은_enum_상수를_중복_선언하면_한_번만_반환한다() throws Exception {
        HandlerMethod handlerMethod = handlerMethod(DuplicateDeclarationController.class, "handle");

        assertThat(errorCodeResolver.resolve(handlerMethod))
                .containsExactly(CommonErrorCode.INTERNAL_ERROR);
    }

    @Test
    void errorStatus가_4xx_또는_5xx가_아니면_ErrorCode를_거부한다() throws Exception {
        HandlerMethod handlerMethod = handlerMethod(SuccessStatusController.class, "handle");

        assertThatThrownBy(() -> errorCodeResolver.resolve(handlerMethod))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("4xx or 5xx");
    }

    private HandlerMethod handlerMethod(Class<?> controllerType, String methodName) throws Exception {
        Object controller = controllerType.getDeclaredConstructor().newInstance();
        Method method = controllerType.getMethod(methodName);
        return new HandlerMethod(controller, method);
    }

    private interface InterfaceAnnotatedApiDocs {

        @ApiErrorCodes(
                type = CommonErrorCode.class,
                names = {"VALIDATION_FAILED", "INTERNAL_ERROR"}
        )
        void handle();
    }

    private static final class InterfaceAnnotatedController implements InterfaceAnnotatedApiDocs {

        @Override
        public void handle() {
        }
    }

    private static final class NonEnumErrorCode implements ErrorCode {

        @Override
        public HttpStatus getStatus() {
            return HttpStatus.BAD_REQUEST;
        }

        @Override
        public String getCode() {
            return "NON_ENUM";
        }

        @Override
        public String getMessage() {
            return "enum이 아닌 오류";
        }
    }

    private static final class NonEnumController {

        @ApiErrorCodes(type = NonEnumErrorCode.class, names = "NON_ENUM")
        public void handle() {
        }
    }

    private static final class UnknownConstantController {

        @ApiErrorCodes(type = CommonErrorCode.class, names = "UNKNOWN_ERROR")
        public void handle() {
        }
    }

    private enum FirstDuplicateErrorCode implements ErrorCode {
        FIRST;

        @Override
        public HttpStatus getStatus() {
            return HttpStatus.CONFLICT;
        }

        @Override
        public String getCode() {
            return "DUPLICATE_CODE";
        }

        @Override
        public String getMessage() {
            return "첫 번째 중복 오류";
        }
    }

    private enum SecondDuplicateErrorCode implements ErrorCode {
        SECOND;

        @Override
        public HttpStatus getStatus() {
            return HttpStatus.CONFLICT;
        }

        @Override
        public String getCode() {
            return "DUPLICATE_CODE";
        }

        @Override
        public String getMessage() {
            return "두 번째 중복 오류";
        }
    }

    private static final class DuplicateCodeController {

        @ApiErrorCodes(type = FirstDuplicateErrorCode.class, names = "FIRST")
        @ApiErrorCodes(type = SecondDuplicateErrorCode.class, names = "SECOND")
        public void handle() {
        }
    }

    private static final class DuplicateDeclarationController {

        @ApiErrorCodes(type = CommonErrorCode.class, names = "INTERNAL_ERROR")
        @ApiErrorCodes(type = CommonErrorCode.class, names = "INTERNAL_ERROR")
        public void handle() {
        }
    }

    private enum SuccessStatusErrorCode implements ErrorCode {
        OK;

        @Override
        public HttpStatus getStatus() {
            return HttpStatus.OK;
        }

        @Override
        public String getCode() {
            return "SUCCESS_CODE";
        }

        @Override
        public String getMessage() {
            return "성공 상태 오류";
        }
    }

    private static final class SuccessStatusController {

        @ApiErrorCodes(type = SuccessStatusErrorCode.class, names = "OK")
        public void handle() {
        }
    }
}
