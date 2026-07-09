package org.sopt.ssingserver.domain.matching.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.sopt.ssingserver.domain.matching.event.InstructorAcceptedEvent;
import org.sopt.ssingserver.domain.matching.event.MatchingConfirmedEvent;
import org.sopt.ssingserver.domain.matching.event.MatchingOfferCanceledEvent;
import org.sopt.ssingserver.domain.matching.event.MatchingOfferClosedEvent;
import org.sopt.ssingserver.domain.matching.event.MatchingOfferCreatedEvent;
import org.sopt.ssingserver.domain.matching.event.MatchingRequestStatusChangedEvent;
import org.sopt.ssingserver.domain.matching.event.PaymentPendingEvent;
import org.sopt.ssingserver.domain.matching.event.PaymentStatusChangedEvent;
import org.sopt.ssingserver.domain.matching.event.RequesterConfirmationUpdatedEvent;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttribute;

class MatchingNotificationContextLoaderTransactionTest {

    @ParameterizedTest
    @MethodSource("loadMethods")
    void 커밋후_context_조회는_새로운_readOnly_트랜잭션을_사용한다(Method method) {
        TransactionAttribute attribute = new AnnotationTransactionAttributeSource()
                .getTransactionAttribute(method, MatchingNotificationContextLoader.class);

        assertThat(attribute).isNotNull();
        assertThat(attribute.isReadOnly()).isTrue();
        assertThat(attribute.getPropagationBehavior())
                .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    private static Stream<Method> loadMethods() throws NoSuchMethodException {
        return Stream.of(
                MatchingNotificationContextLoader.class.getMethod("load", MatchingOfferCreatedEvent.class),
                MatchingNotificationContextLoader.class.getMethod("load", MatchingRequestStatusChangedEvent.class),
                MatchingNotificationContextLoader.class.getMethod("load", InstructorAcceptedEvent.class),
                MatchingNotificationContextLoader.class.getMethod("load", RequesterConfirmationUpdatedEvent.class),
                MatchingNotificationContextLoader.class.getMethod("load", PaymentPendingEvent.class),
                MatchingNotificationContextLoader.class.getMethod("load", PaymentStatusChangedEvent.class),
                MatchingNotificationContextLoader.class.getMethod("load", MatchingConfirmedEvent.class),
                MatchingNotificationContextLoader.class.getMethod("load", MatchingOfferClosedEvent.class),
                MatchingNotificationContextLoader.class.getMethod("load", MatchingOfferCanceledEvent.class)
        );
    }
}
