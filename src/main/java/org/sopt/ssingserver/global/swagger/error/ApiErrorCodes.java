package org.sopt.ssingserver.global.swagger.error;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.sopt.ssingserver.global.error.ErrorCode;

@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(ApiErrorCodes.List.class)
public @interface ApiErrorCodes {

    Class<? extends ErrorCode> type();

    String[] names();

    @Documented
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface List {

        ApiErrorCodes[] value();
    }
}
