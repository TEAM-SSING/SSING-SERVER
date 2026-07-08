package org.sopt.ssingserver.global.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = RequestedDurationsValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidRequestedDurations {

    String message() default "요청 값이 올바르지 않습니다.";

    String notAllowedMessage();

    String duplicatedMessage();

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    int[] allowedValues();
}
