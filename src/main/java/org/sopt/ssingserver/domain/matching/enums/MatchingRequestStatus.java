package org.sopt.ssingserver.domain.matching.enums;

import java.util.List;

public enum MatchingRequestStatus {
    REQUESTED,
    GROUPED,
    MATCHED,
    CONFIRMED,
    COMPLETED,
    CANCELED,
    EXPIRED,
    FAILED;

    private static final List<MatchingRequestStatus> ACTIVE_NEGOTIATION_STATUSES = List.of(
            REQUESTED,
            GROUPED,
            MATCHED
    );

    public boolean isActiveNegotiation() {
        return ACTIVE_NEGOTIATION_STATUSES.contains(this);
    }

    public static List<MatchingRequestStatus> activeNegotiationStatuses() {
        return ACTIVE_NEGOTIATION_STATUSES;
    }
}
