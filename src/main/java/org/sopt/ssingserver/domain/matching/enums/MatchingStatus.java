package org.sopt.ssingserver.domain.matching.enums;

// DB 저장 상태가 아닌 Android 화면 전환용 API 표시 상태
public enum MatchingStatus {
    // DB REQUESTED 저장 상태에서 그룹/제안 없음 및 탐색 중인 경우의 앱 SEARCHING 표시
    SEARCHING,
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
