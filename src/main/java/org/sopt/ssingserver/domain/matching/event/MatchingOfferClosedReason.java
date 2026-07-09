package org.sopt.ssingserver.domain.matching.event;

// 매칭 상태값 정의의 강사 제안 종료 사유
public enum MatchingOfferClosedReason {

    REJECTED,
    EXPIRED,
    ACCEPTED_BY_OTHER_INSTRUCTOR,
    GROUP_CANCELED
}
