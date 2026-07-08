package org.sopt.ssingserver.domain.matching.service;

import java.util.Optional;
import org.sopt.ssingserver.domain.matching.entity.MatchingOffer;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequest;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroup;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatusReason;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
import org.sopt.ssingserver.global.error.BusinessException;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.springframework.stereotype.Component;

// DB 상태와 주변 객체 존재 여부 조합 기반 앱 표시 matchingStatus 계산
@Component
public class MatchingStatusResolver {

    // DB enum에 없는 SEARCHING 등 표시 상태 계산과 API/상태 조회 기준 통일
    public MatchingStatus resolve(
            MatchingRequest matchingRequest,
            Optional<MatchingRequestGroup> matchingRequestGroup,
            Optional<MatchingOffer> matchingOffer
    ) {
        // fallback 탐색 만료 실패 요청의 앱 최종 후보 없음 상태 표시
        if (matchingRequest.getStatus() == MatchingRequestStatus.FAILED
                && matchingRequest.getStatusReason() == MatchingRequestStatusReason.NO_AVAILABLE_INSTRUCTOR) {
            return MatchingStatus.NO_AVAILABLE_INSTRUCTOR;
        }

        // 기타 실패 요청의 앱 일반 실패 상태 표시
        if (matchingRequest.getStatus() == MatchingRequestStatus.FAILED) {
            return MatchingStatus.FAILED;
        }

        // 소비자 취소 등 요청 자체 종료 상태의 그룹/제안보다 우선 표시
        if (matchingRequest.getStatus() == MatchingRequestStatus.CANCELED) {
            return MatchingStatus.CANCELED;
        }

        // 강사 수락 이후에는 남아 있는 group/offer row보다 소비자 최종 확인 대기 상태 우선 계산
        if (matchingRequest.getStatus() == MatchingRequestStatus.MATCHED) {
            return MatchingStatus.WAITING_FOR_CONFIRMATION;
        }

        // 요청 최종 확정 이후에는 이전 제안/그룹 상태보다 강습 생성 완료 상태 우선 계산
        if (matchingRequest.getStatus() == MatchingRequestStatus.CONFIRMED) {
            return MatchingStatus.CONFIRMED;
        }

        // 연결된 강습 완료 이후에도 매칭 화면에서는 확정된 매칭으로 표시
        if (matchingRequest.getStatus() == MatchingRequestStatus.COMPLETED) {
            return MatchingStatus.CONFIRMED;
        }

        // DB REQUESTED 유지 중 팀 결합 정책상 그룹은 있으나 제안 전이면 팀 대기 상태 표시
        if (matchingRequest.getStatus() == MatchingRequestStatus.REQUESTED) {
            if (matchingRequestGroup.isPresent()) {
                return MatchingStatus.WAITING_FOR_TEAM;
            }
            return MatchingStatus.SEARCHING;
        }

        // 그룹 생성과 강사 제안 이후 소비자 화면의 강사 응답 대기 상태 표시
        if (matchingOffer.isPresent()) {
            return MatchingStatus.WAITING_FOR_INSTRUCTOR;
        }

        // 추후 minHeadcount/요청 결합 정책 도입 시 그룹 생성 후 제안 전 대기 상태 표시
        if (matchingRequestGroup.isPresent()) {
            return MatchingStatus.WAITING_FOR_TEAM;
        }

        // 정의되지 않은 상태 조합의 조용한 FAILED 치환 방지
        throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
    }
}
