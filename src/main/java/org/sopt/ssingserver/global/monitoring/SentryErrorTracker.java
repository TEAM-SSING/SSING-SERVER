package org.sopt.ssingserver.global.monitoring;

import jakarta.servlet.http.HttpServletRequest;
import io.sentry.Sentry;
import io.sentry.SentryLevel;
import java.util.function.BiConsumer;
import org.sopt.ssingserver.global.error.ErrorCode;
import org.sopt.ssingserver.global.logging.RequestIdFilter;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;

@Component
@Profile("dev")
public class SentryErrorTracker implements ErrorTracker {

    private static final String REQUEST_ID_TAG = "request_id";
    private static final String EVENT_TAG = "event";
    private static final String ERROR_CODE_TAG = "error_code";
    private static final String STATUS_TAG = "status";
    private static final String EXCEPTION_TYPE_TAG = "exception_type";
    private static final String METHOD_TAG = "method";
    private static final String ROUTE_TAG = "route";
    private static final String SERVICE_TAG = "service";
    private static final String RAW_PATH_TAG = "raw_path";
    static final String MANAGED_BY_TAG = "managed_by";
    static final String MANAGED_BY = "ssing-error-tracker";

    private static final String SERVICE_NAME = "ssing-server";

    @Override
    public void capture(String eventName, ErrorCode errorCode, Throwable exception, HttpServletRequest request) {
        try {
            EventData event = EventData.from(eventName, errorCode, exception, request);
            Sentry.withScope(scope -> {
                setTag(scope::setTag, REQUEST_ID_TAG, event.requestId());
                setTag(scope::setTag, EVENT_TAG, event.eventName());
                setTag(scope::setTag, ERROR_CODE_TAG, event.errorCode());
                setTag(scope::setTag, STATUS_TAG, event.status());
                setTag(scope::setTag, EXCEPTION_TYPE_TAG, event.exceptionType());
                setTag(scope::setTag, METHOD_TAG, event.method());
                setTag(scope::setTag, ROUTE_TAG, event.route());
                setTag(scope::setTag, SERVICE_TAG, SERVICE_NAME);
                setTag(scope::setTag, MANAGED_BY_TAG, MANAGED_BY);
                Sentry.captureException(exception);
            });
        } catch (RuntimeException ignored) {
            // Sentry transport or SDK failures must never affect the user request.
        }
    }

    @Override
    public void captureUnexpectedClientError(HttpServletRequest request, int status) {
        try {
            ClientErrorData event = ClientErrorData.from(request, status);
            Sentry.withScope(scope -> {
                setTag(scope::setTag, REQUEST_ID_TAG, event.requestId());
                setTag(scope::setTag, EVENT_TAG, event.eventName());
                setTag(scope::setTag, STATUS_TAG, event.status());
                setTag(scope::setTag, METHOD_TAG, event.method());
                setTag(scope::setTag, ROUTE_TAG, event.route());
                setTag(scope::setTag, RAW_PATH_TAG, event.rawPath());
                setTag(scope::setTag, SERVICE_TAG, SERVICE_NAME);
                setTag(scope::setTag, MANAGED_BY_TAG, MANAGED_BY);
                scope.setLevel(SentryLevel.WARNING);
                Sentry.captureMessage("Unexpected client error");
            });
        } catch (RuntimeException ignored) {
            // Sentry transport or SDK failures must never affect the user request.
        }
    }

    private void setTag(BiConsumer<String, String> tagSetter, String name, Object value) {
        if (value == null) {
            return;
        }
        String tagValue = value.toString();
        if (!tagValue.isBlank()) {
            tagSetter.accept(name, tagValue);
        }
    }

    record EventData(
            String eventName,
            String requestId,
            String errorCode,
            int status,
            String exceptionType,
            String method,
            String route
    ) {

        static EventData from(
                String eventName,
                ErrorCode errorCode,
                Throwable exception,
                HttpServletRequest request
        ) {
            return new EventData(
                    eventName,
                    requestId(request),
                    errorCode.getCode(),
                    errorCode.getStatus().value(),
                    exception.getClass().getName(),
                    request.getMethod(),
                    route(request)
            );
        }

        private static String requestId(HttpServletRequest request) {
            Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
            return requestId == null ? null : requestId.toString();
        }

        private static String route(HttpServletRequest request) {
            Object routeTemplate = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            return routeTemplate == null ? "/unmapped" : routeTemplate.toString();
        }
    }

    record ClientErrorData(
            String eventName,
            String requestId,
            int status,
            String method,
            String route,
            String rawPath
    ) {

        static ClientErrorData from(HttpServletRequest request, int status) {
            String route = EventData.route(request);
            return new ClientErrorData(
                    "http.request.unexpected_client_error",
                    EventData.requestId(request),
                    status,
                    request.getMethod(),
                    route,
                    "/unmapped".equals(route) ? RequestPathSanitizer.rawPath(request) : null
            );
        }
    }
}
