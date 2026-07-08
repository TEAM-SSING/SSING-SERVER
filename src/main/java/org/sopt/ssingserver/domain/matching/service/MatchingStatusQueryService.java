package org.sopt.ssingserver.domain.matching.service;

import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.lesson.entity.Lesson;
import org.sopt.ssingserver.domain.lesson.repository.LessonRepository;
import org.sopt.ssingserver.domain.matching.dto.result.MatchingStatusQueryResult;
import org.sopt.ssingserver.domain.matching.entity.MatchingOffer;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequest;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroup;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroupItem;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
import org.sopt.ssingserver.domain.matching.error.MatchingErrorCode;
import org.sopt.ssingserver.domain.matching.repository.MatchingOfferRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestGroupItemRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestRepository;
import org.sopt.ssingserver.domain.payment.entity.MatchingRequestPayment;
import org.sopt.ssingserver.domain.payment.repository.MatchingRequestPaymentRepository;
import org.sopt.ssingserver.global.error.BusinessException;
import org.sopt.ssingserver.global.error.CommonErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MatchingStatusQueryService {

    private final MatchingRequestRepository matchingRequestRepository;
    private final MatchingRequestGroupItemRepository matchingRequestGroupItemRepository;
    private final MatchingOfferRepository matchingOfferRepository;
    private final MatchingRequestPaymentRepository matchingRequestPaymentRepository;
    private final LessonRepository lessonRepository;
    private final MatchingStatusResolver matchingStatusResolver;

    @Transactional(readOnly = true)
    public MatchingStatusQueryResult getStatus(
            Long memberId,
            Long matchingRequestId
    ) {
        MatchingRequest matchingRequest = matchingRequestRepository.findById(matchingRequestId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.MATCHING_REQUEST_NOT_FOUND));

        validateOwner(memberId, matchingRequest);

        // 조회 API는 상태를 바꾸지 않고 현재 요청 주변 row를 모아 Android 복구 응답을 만든다.
        Optional<MatchingRequestGroupItem> matchingRequestGroupItem =
                matchingRequestGroupItemRepository.findFirstByMatchingRequestIdOrderByIdDesc(matchingRequestId);
        Optional<MatchingRequestGroup> matchingRequestGroup = matchingRequestGroupItem
                .map(MatchingRequestGroupItem::getMatchingRequestGroup);
        Optional<MatchingOffer> matchingOffer = findCurrentOffer(matchingRequest, matchingRequestGroup);
        Optional<MatchingRequestPayment> matchingRequestPayment =
                matchingRequestPaymentRepository.findFirstByMatchingRequestIdOrderByIdDesc(matchingRequestId);
        Optional<Lesson> lesson = findLesson(matchingOffer);

        MatchingStatus matchingStatus = matchingStatusResolver.resolve(
                matchingRequest,
                matchingRequestGroup,
                matchingRequestGroupItem,
                matchingOffer,
                matchingRequestPayment
        );

        return MatchingStatusQueryResult.of(
                matchingRequest,
                matchingStatus,
                matchingRequestGroupItem,
                matchingOffer,
                matchingRequestPayment,
                lesson
        );
    }

    private void validateOwner(
            Long memberId,
            MatchingRequest matchingRequest
    ) {
        Long ownerId = matchingRequest.getMember().getId();
        // 컨트롤러 권한 통과 이후에도 요청 소유자만 조회 가능한 도메인 보안 경계
        if (!Objects.equals(ownerId, memberId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
    }

    private Optional<MatchingOffer> findCurrentOffer(
            MatchingRequest matchingRequest,
            Optional<MatchingRequestGroup> matchingRequestGroup
    ) {
        if (matchingRequest.getMatchingOffer() != null) {
            return Optional.of(matchingRequest.getMatchingOffer());
        }

        return matchingRequestGroup
                .map(MatchingRequestGroup::getId)
                .filter(Objects::nonNull)
                .flatMap(matchingOfferRepository::findFirstByMatchingRequestGroupIdOrderByIdDesc);
    }

    private Optional<Lesson> findLesson(Optional<MatchingOffer> matchingOffer) {
        return matchingOffer
                .map(MatchingOffer::getId)
                .filter(Objects::nonNull)
                .flatMap(lessonRepository::findByMatchingOfferId);
    }
}
