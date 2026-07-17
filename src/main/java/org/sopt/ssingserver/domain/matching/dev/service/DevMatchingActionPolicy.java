package org.sopt.ssingserver.domain.matching.dev.service;

import java.util.EnumSet;
import java.util.Set;
import org.sopt.ssingserver.domain.matching.dev.enums.DevMatchingActionKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile({"local", "dev"})
@Component
class DevMatchingActionPolicy {

    private static final Set<DevMatchingActionKey> EXECUTABLE_ACTIONS = EnumSet.of(
            DevMatchingActionKey.INSTRUCTOR_ACCEPT,
            DevMatchingActionKey.CONSUMER_ACCEPT,
            DevMatchingActionKey.PAYMENT_COMPLETE
    );

    private final boolean actionsEnabled;

    DevMatchingActionPolicy(
            @Value("${ssing.dev-matching-actions.enabled:false}") boolean actionsEnabled
    ) {
        this.actionsEnabled = actionsEnabled;
    }

    boolean isExecutable(DevMatchingActionContext context, DevMatchingActionKey actionKey) {
        return actionsEnabled
                && context.requests().size() == 1
                && EXECUTABLE_ACTIONS.contains(actionKey);
    }
}
