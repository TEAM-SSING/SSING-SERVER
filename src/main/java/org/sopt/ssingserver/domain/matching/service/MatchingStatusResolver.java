package org.sopt.ssingserver.domain.matching.service;

import java.util.Optional;
import org.sopt.ssingserver.domain.matching.entity.MatchingOffer;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequest;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroup;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatusReason;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
import org.springframework.stereotype.Component;

// DB 상태와 주변 객체 존재 여부 조합 기반 앱 표시 matchingStatus 계산
@Component
public class MatchingStatusResolver {

    // DB enum에 없는 SEARCHING 등 표시 상태 계산과 API/상태 조회 기준 통일
    public MatchingStatus resolve(
            MatchingRequest matchingRequest,
            boolean hasAvailableCandidate,
            Optional<MatchingRequestGroup> matchingRequestGroup,
            Optional<MatchingOffer> matchingOffer
    ) {
        // 5분 탐색 만료 실패 요청의 앱 최종 후보 없음 상태 표시
        if (matchingRequest.getStatus() == MatchingRequestStatus.FAILED
                && matchingRequest.getStatusReason() == MatchingRequestStatusReason.NO_AVAILABLE_INSTRUCTOR) {
            return MatchingStatus.NO_AVAILABLE_INSTRUCTOR;
        }

        // 소비자 취소 등 요청 자체 종료 상태의 그룹/제안보다 우선 표시
        if (matchingRequest.getStatus() == MatchingRequestStatus.CANCELED) {
            return MatchingStatus.CANCELED;
        }

        // 강사 제안 row 생성 이후 소비자 화면의 강사 응답 대기 상태 표시
        if (matchingOffer.isPresent()) {
            return MatchingStatus.WAITING_FOR_INSTRUCTOR;
        }

        // 그룹 생성 후 제안 전 상태의 팀 정원 또는 제안 준비 미완료 표시
        if (matchingRequestGroup.isPresent()) {
            return MatchingStatus.WAITING_FOR_TEAM;
        }

        // DB REQUESTED 유지 중 그룹/제안 없음 상태의 앱 SEARCHING 표시
        // WAITING_FOR_TEAM은 추후 minHeadcount/요청 결합 정책 도입 시 재개
        if (matchingRequest.getStatus() == MatchingRequestStatus.REQUESTED) {
            return MatchingStatus.SEARCHING;
        }

        // 후속 API 연결 시 세분화 예정인 기본 매핑
        if (matchingRequest.getStatus() == MatchingRequestStatus.MATCHED) {
            return MatchingStatus.WAITING_FOR_CONFIRMATION;
        }

        if (matchingRequest.getStatus() == MatchingRequestStatus.CONFIRMED) {
            return MatchingStatus.PAYMENT_PENDING;
        }

        if (matchingRequest.getStatus() == MatchingRequestStatus.COMPLETED) {
            return MatchingStatus.CONFIRMED;
        }

        return MatchingStatus.FAILED;
    }
}
