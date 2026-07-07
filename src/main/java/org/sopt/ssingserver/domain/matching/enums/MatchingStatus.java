package org.sopt.ssingserver.domain.matching.enums;

// DB 저장 상태가 아니라 Android 화면 전환을 위해 계산해서 내려주는 API 표시 상태
public enum MatchingStatus {
    NO_AVAILABLE_INSTRUCTOR,
    WAITING_FOR_TEAM,
    WAITING_FOR_INSTRUCTOR,
    WAITING_FOR_CONFIRMATION,
    WAITING_FOR_OTHER_CONFIRMATIONS,
    PAYMENT_PENDING,
    WAITING_FOR_OTHER_PAYMENTS,
    CONFIRMED,
    REMATCHING,
    PAYMENT_EXPIRED,
    CANCELED,
    FAILED
}
