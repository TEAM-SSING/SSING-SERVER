package org.sopt.ssingserver.domain.matching.dev.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.auth.dev.entity.DevPersona;
import org.sopt.ssingserver.domain.auth.dev.repository.DevPersonaRepository;
import org.sopt.ssingserver.domain.lesson.entity.Lesson;
import org.sopt.ssingserver.domain.lesson.enums.LessonStatus;
import org.sopt.ssingserver.domain.lesson.repository.LessonRepository;
import org.sopt.ssingserver.domain.matching.dev.dto.response.DevMatchingActionPreviewResponse;
import org.sopt.ssingserver.domain.matching.dev.dto.response.DevMatchingParticipantResponse;
import org.sopt.ssingserver.domain.matching.dev.dto.response.DevMatchingPersonResponse;
import org.sopt.ssingserver.domain.matching.dev.dto.response.DevMatchingRequestDetailResponse;
import org.sopt.ssingserver.domain.matching.dev.dto.response.DevMatchingRequestListResponse;
import org.sopt.ssingserver.domain.matching.dev.dto.response.DevMatchingRequestRelationResponse;
import org.sopt.ssingserver.domain.matching.dev.dto.response.DevMatchingRequestSummaryResponse;
import org.sopt.ssingserver.domain.matching.dev.dto.response.DevMatchingResourceResponse;
import org.sopt.ssingserver.domain.matching.dev.enums.DevMatchingPersonRole;
import org.sopt.ssingserver.domain.matching.dev.enums.DevMatchingResolutionState;
import org.sopt.ssingserver.domain.matching.dev.enums.DevMatchingResourceType;
import org.sopt.ssingserver.domain.matching.entity.MatchingOffer;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequest;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroup;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestGroupItem;
import org.sopt.ssingserver.domain.matching.entity.MatchingRequestParticipant;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupItemStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatusReason;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
import org.sopt.ssingserver.domain.matching.error.MatchingErrorCode;
import org.sopt.ssingserver.domain.matching.repository.MatchingOfferRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestGroupItemRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestParticipantRepository;
import org.sopt.ssingserver.domain.matching.repository.MatchingRequestRepository;
import org.sopt.ssingserver.domain.matching.service.MatchingStatusResolver;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.payment.entity.MatchingRequestPayment;
import org.sopt.ssingserver.domain.payment.enums.MatchingRequestPaymentStatus;
import org.sopt.ssingserver.domain.payment.repository.MatchingOfferPriceSnapshotRepository;
import org.sopt.ssingserver.domain.payment.repository.MatchingRequestPaymentRepository;
import org.sopt.ssingserver.global.error.BusinessException;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Profile({"local", "dev"})
@Service
@RequiredArgsConstructor
public class DevMatchingQueryService {

    private final MatchingRequestRepository matchingRequestRepository;
    private final MatchingRequestGroupItemRepository matchingRequestGroupItemRepository;
    private final MatchingOfferRepository matchingOfferRepository;
    private final MatchingOfferPriceSnapshotRepository matchingOfferPriceSnapshotRepository;
    private final MatchingRequestPaymentRepository matchingRequestPaymentRepository;
    private final MatchingRequestParticipantRepository matchingRequestParticipantRepository;
    private final LessonRepository lessonRepository;
    private final DevPersonaRepository devPersonaRepository;
    private final MatchingStatusResolver matchingStatusResolver;
    private final DevMatchingActionPreviewFactory actionPreviewFactory;
    private final DevMatchingStateTokenFactory stateTokenFactory;
    private final Clock clock;

    @Transactional(readOnly = true)
    public DevMatchingRequestListResponse getRequests(int page, int size) {
        Instant observedAt = clock.instant();
        Page<MatchingRequest> requestPage = matchingRequestRepository.findAllByOrderByIdDesc(
                PageRequest.of(page, size)
        );
        List<CaseContext> contexts = loadContexts(requestPage.getContent());
        List<DevMatchingRequestSummaryResponse> requests = contexts.stream()
                .map(this::toSummary)
                .toList();
        return new DevMatchingRequestListResponse(
                observedAt,
                requestPage.getNumber(),
                requestPage.getSize(),
                requestPage.getTotalElements(),
                requestPage.getTotalPages(),
                requestPage.hasNext(),
                requests
        );
    }

    @Transactional(readOnly = true)
    public DevMatchingRequestDetailResponse getRequest(Long matchingRequestId) {
        MatchingRequest matchingRequest = matchingRequestRepository.findDevExplorerContextById(matchingRequestId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.MATCHING_REQUEST_NOT_FOUND));
        CaseContext context = loadContexts(List.of(matchingRequest)).getFirst();
        Instant observedAt = clock.instant();
        List<DevMatchingPersonResponse> people = people(context);
        List<DevMatchingRequestRelationResponse> requestRelations = requestRelations(context);
        List<DevMatchingParticipantResponse> participants = participants(context);
        List<DevMatchingResourceResponse> resources = resources(context);
        List<DevMatchingActionPreviewResponse> actions = actions(context);
        String stateToken = stateTokenFactory.create(
                matchingRequestId,
                context.matchingStatus(),
                people,
                requestRelations,
                participants,
                resources,
                actions
        );
        return new DevMatchingRequestDetailResponse(
                observedAt,
                stateToken,
                matchingRequestId,
                context.resolutionState(),
                context.matchingStatus(),
                matchingRequest.getStatus(),
                matchingRequest.getStatusReason(),
                people,
                requestRelations,
                participants,
                resources,
                actions,
                context.diagnostics(),
                actionLimitations(context)
        );
    }

    private List<CaseContext> loadContexts(List<MatchingRequest> requests) {
        if (requests.isEmpty()) {
            return List.of();
        }

        // 페이지 크기와 무관하게 고정된 batch query들로 현재 관계와 충돌 이력을 함께 조립한다.
        List<Long> requestIds = requests.stream().map(MatchingRequest::getId).toList();
        List<MatchingRequestGroupItem> itemHistories = new ArrayList<>(matchingRequestGroupItemRepository
                .findHistoryByMatchingRequestIdInOrderByRequestIdAscItemIdAsc(requestIds));
        Map<Long, List<MatchingRequestGroupItem>> itemHistoryByRequestId = groupItemsByRequestId(itemHistories);
        Map<Long, MatchingRequestGroupItem> currentItemByRequestId = currentItems(itemHistoryByRequestId);
        Set<Long> groupIds = currentItemByRequestId.values().stream()
                .map(MatchingRequestGroupItem::getMatchingRequestGroup)
                .map(MatchingRequestGroup::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<MatchingRequestGroupItem> allGroupItems = groupIds.isEmpty()
                ? List.of()
                : matchingRequestGroupItemRepository
                        .findByMatchingRequestGroupIdInOrderByMatchingRequestGroupIdAscIdAsc(groupIds);
        Map<Long, List<MatchingRequestGroupItem>> groupItemsByGroupId = allGroupItems.stream()
                .collect(Collectors.groupingBy(
                        item -> item.getMatchingRequestGroup().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        Set<Long> relatedRequestIds = new LinkedHashSet<>(requestIds);
        allGroupItems.stream()
                .map(MatchingRequestGroupItem::getMatchingRequest)
                .map(MatchingRequest::getId)
                .forEach(relatedRequestIds::add);
        Set<Long> newlyRelatedRequestIds = new LinkedHashSet<>(relatedRequestIds);
        newlyRelatedRequestIds.removeAll(requestIds);
        if (!newlyRelatedRequestIds.isEmpty()) {
            itemHistories.addAll(matchingRequestGroupItemRepository
                    .findHistoryByMatchingRequestIdInOrderByRequestIdAscItemIdAsc(newlyRelatedRequestIds));
            itemHistoryByRequestId = groupItemsByRequestId(itemHistories);
            currentItemByRequestId = currentItems(itemHistoryByRequestId);
        }

        List<MatchingOffer> groupOffers = groupIds.isEmpty()
                ? List.of()
                : matchingOfferRepository.findByMatchingRequestGroupIdInOrderByGroupIdAscOfferIdDesc(groupIds);
        Map<Long, List<MatchingOffer>> groupOffersByGroupId = groupOffers.stream()
                .collect(Collectors.groupingBy(
                        offer -> offer.getMatchingRequestGroup().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        Map<Long, MatchingOffer> currentOfferByRequestId = new LinkedHashMap<>();
        for (MatchingRequest request : requests) {
            MatchingRequestGroupItem currentItem = currentItemByRequestId.get(request.getId());
            MatchingRequestGroup group = currentItem == null ? null : currentItem.getMatchingRequestGroup();
            List<MatchingOffer> offers = group == null
                    ? List.of()
                    : groupOffersByGroupId.getOrDefault(group.getId(), List.of());
            MatchingOffer currentOffer = resolveCurrentOffer(request, group, offers);
            if (currentOffer != null) {
                currentOfferByRequestId.put(request.getId(), currentOffer);
            }
        }

        Set<Long> offerIds = currentOfferByRequestId.values().stream()
                .map(MatchingOffer::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, Long> priceSnapshotCountByOfferId = offerIds.isEmpty()
                ? Map.of()
                : matchingOfferPriceSnapshotRepository.findByMatchingOfferIdIn(offerIds)
                        .stream()
                        .collect(Collectors.groupingBy(
                                snapshot -> snapshot.getMatchingOffer().getId(),
                                LinkedHashMap::new,
                                Collectors.counting()
                        ));
        List<MatchingRequestPayment> payments = offerIds.isEmpty()
                ? List.of()
                : matchingRequestPaymentRepository
                        .findByMatchingOfferIdInOrderByOfferIdAscRequestIdAscPaymentIdDesc(offerIds);
        Map<Long, List<MatchingRequestPayment>> paymentsByOfferId = payments.stream()
                .collect(Collectors.groupingBy(
                        payment -> payment.getMatchingOffer().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        Map<Long, Lesson> lessonByOfferId = offerIds.isEmpty()
                ? Map.of()
                : lessonRepository.findByMatchingOfferIdInOrderByOfferIdAscLessonIdDesc(offerIds)
                        .stream()
                        .collect(Collectors.toMap(
                                lesson -> lesson.getMatchingOffer().getId(),
                                Function.identity(),
                                (first, ignored) -> first,
                                LinkedHashMap::new
                        ));

        List<MatchingRequestParticipant> allParticipants = matchingRequestParticipantRepository
                .findByMatchingRequestIdInOrderByMatchingRequestIdAscIdAsc(relatedRequestIds);
        Map<Long, List<MatchingRequestParticipant>> participantsByRequestId = allParticipants.stream()
                .collect(Collectors.groupingBy(
                        participant -> participant.getMatchingRequest().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        Set<Long> liveInstructorProfileIds = currentOfferByRequestId.values().stream()
                .filter(this::isLiveOffer)
                .map(MatchingOffer::getInstructorProfile)
                .map(profile -> profile.getId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, List<MatchingOffer>> liveOffersByInstructorProfileId = liveInstructorProfileIds.isEmpty()
                ? Map.of()
                : matchingOfferRepository.findActiveByInstructorProfileIdIn(
                                liveInstructorProfileIds,
                                MatchingOfferStatus.OFFERED,
                                MatchingOfferStatus.ACCEPTED,
                                liveAcceptedGroupStatuses()
                        )
                        .stream()
                        .collect(Collectors.groupingBy(
                                offer -> offer.getInstructorProfile().getId(),
                                LinkedHashMap::new,
                                Collectors.toList()
                        ));

        Set<Long> memberIds = new LinkedHashSet<>();
        requests.stream().map(MatchingRequest::getMember).map(Member::getId).forEach(memberIds::add);
        allGroupItems.stream()
                .map(MatchingRequestGroupItem::getMatchingRequest)
                .map(MatchingRequest::getMember)
                .map(Member::getId)
                .forEach(memberIds::add);
        currentOfferByRequestId.values().stream()
                .map(MatchingOffer::getInstructorProfile)
                .map(profile -> profile.getMember().getId())
                .forEach(memberIds::add);
        Map<Long, DevPersona> personaByMemberId = memberIds.isEmpty()
                ? Map.of()
                : devPersonaRepository.findByMemberIdIn(memberIds)
                        .stream()
                        .collect(Collectors.toMap(
                                persona -> persona.getMember().getId(),
                                Function.identity()
                        ));

        List<CaseContext> contexts = new ArrayList<>();
        for (MatchingRequest request : requests) {
            MatchingRequestGroupItem currentItem = currentItemByRequestId.get(request.getId());
            MatchingRequestGroup group = currentItem == null ? null : currentItem.getMatchingRequestGroup();
            List<MatchingRequestGroupItem> groupItems = group == null
                    ? List.of()
                    : groupItemsByGroupId.getOrDefault(group.getId(), List.of());
            List<MatchingOffer> offers = group == null
                    ? List.of()
                    : groupOffersByGroupId.getOrDefault(group.getId(), List.of());
            MatchingOffer offer = currentOfferByRequestId.get(request.getId());
            List<MatchingRequestPayment> offerPayments = offer == null
                    ? List.of()
                    : paymentsByOfferId.getOrDefault(offer.getId(), List.of());
            MatchingRequestPayment selectedPayment = findPayment(offerPayments, request.getId());
            Lesson lesson = offer == null ? null : lessonByOfferId.get(offer.getId());

            List<MatchingRequest> relatedRequests = relatedRequests(request, groupItems);
            Map<Long, MatchingRequestGroupItem> itemByRequestId = groupItems.stream()
                    .collect(Collectors.toMap(
                            item -> item.getMatchingRequest().getId(),
                            Function.identity()
                    ));
            Map<Long, MatchingStatus> statusesByRequestId = new LinkedHashMap<>();
            List<String> diagnostics = new ArrayList<>();
            for (MatchingRequest relatedRequest : relatedRequests) {
                MatchingRequestGroupItem relatedItem = itemByRequestId.get(relatedRequest.getId());
                if (relatedItem == null && relatedRequest.getId().equals(request.getId())) {
                    relatedItem = currentItem;
                }
                List<MatchingRequestGroupItem> relatedHistory = itemHistoryByRequestId
                        .getOrDefault(relatedRequest.getId(), List.of());
                validateActiveGroupCardinality(relatedRequest, relatedHistory, diagnostics);
                validateRelatedCurrentGroup(
                        relatedRequest,
                        group,
                        currentItemByRequestId.get(relatedRequest.getId()),
                        diagnostics
                );
                validateParticipantCoverage(
                        relatedRequest,
                        participantsByRequestId.getOrDefault(relatedRequest.getId(), List.of()),
                        diagnostics
                );
                MatchingRequestPayment relatedPayment = findPayment(offerPayments, relatedRequest.getId());
                MatchingStatus matchingStatus = resolveStatus(
                        relatedRequest,
                        group,
                        relatedItem,
                        offer,
                        relatedPayment,
                        diagnostics
                );
                statusesByRequestId.put(relatedRequest.getId(), matchingStatus);
                validateRelations(
                        relatedRequest,
                        relatedItem,
                        group,
                        groupItems,
                        offer,
                        relatedPayment,
                        matchingStatus,
                        diagnostics
                );
                validateLessonState(relatedRequest, offer, lesson, diagnostics);
            }
            MatchingStatus selectedStatus = statusesByRequestId.get(request.getId());
            validateGroupOfferCardinality(group, offers, diagnostics);
            validateInstructorLiveOfferCardinality(offer, liveOffersByInstructorProfileId, diagnostics);
            validatePriceSnapshot(offer, priceSnapshotCountByOfferId, diagnostics);
            validatePaymentCoverage(group, groupItems, offerPayments, diagnostics);
            validateLessonHeadcount(group, offer, lesson, relatedRequests, diagnostics);
            if (statusesByRequestId.values().stream().anyMatch(Objects::isNull)) {
                diagnostics.add("groupId=" + id(group) + ": 관련 요청 중 계산할 수 없는 matchingStatus가 있습니다.");
            }
            List<String> distinctDiagnostics = List.copyOf(new LinkedHashSet<>(diagnostics));
            DevMatchingResolutionState resolutionState = distinctDiagnostics.isEmpty()
                    ? DevMatchingResolutionState.RESOLVED
                    : DevMatchingResolutionState.INCONSISTENT;
            if (resolutionState == DevMatchingResolutionState.INCONSISTENT) {
                selectedStatus = null;
            }

            List<MatchingRequestParticipant> contextParticipants = relatedRequests.stream()
                    .map(MatchingRequest::getId)
                    .map(id -> participantsByRequestId.getOrDefault(id, List.of()))
                    .flatMap(Collection::stream)
                    .toList();
            contexts.add(new CaseContext(
                    request,
                    currentItem,
                    group,
                    offer,
                    selectedPayment,
                    lesson,
                    groupItems,
                    offerPayments,
                    contextParticipants,
                    personaByMemberId,
                    relatedRequests,
                    statusesByRequestId,
                    selectedStatus,
                    resolutionState,
                    distinctDiagnostics
            ));
        }
        return contexts;
    }

    private DevMatchingRequestSummaryResponse toSummary(CaseContext context) {
        DevMatchingPersonResponse consumer = consumer(context.request(), context.personaByMemberId());
        List<DevMatchingActionPreviewResponse> actions = actions(context);
        return new DevMatchingRequestSummaryResponse(
                context.request().getId(),
                consumer.memberId(),
                consumer.personaKey(),
                consumer.displayName(),
                context.resolutionState(),
                context.matchingStatus(),
                context.request().getStatus(),
                context.request().getStatusReason(),
                id(context.group()),
                id(context.offer()),
                actions.stream().map(DevMatchingActionPreviewResponse::actionKey).toList(),
                context.diagnostics(),
                context.request().getUpdatedAt()
        );
    }

    private List<DevMatchingActionPreviewResponse> actions(CaseContext context) {
        if (context.resolutionState() != DevMatchingResolutionState.RESOLVED) {
            return List.of();
        }
        return actionPreviewFactory.create(actionContext(context));
    }

    private DevMatchingActionContext actionContext(CaseContext context) {
        Map<Long, MatchingRequestGroupItem> itemByRequestId = context.groupItems().stream()
                .collect(Collectors.toMap(
                        item -> item.getMatchingRequest().getId(),
                        Function.identity()
                ));
        List<DevMatchingActionContext.RequestState> requestStates = context.relatedRequests().stream()
                .map(request -> {
                    MatchingRequestGroupItem item = itemByRequestId.get(request.getId());
                    if (item == null && request.getId().equals(context.request().getId())) {
                        item = context.currentItem();
                    }
                    return new DevMatchingActionContext.RequestState(
                            request.getId(),
                            request.getStatus(),
                            id(item),
                            item == null ? null : item.getStatus(),
                            context.statusesByRequestId().get(request.getId()),
                            consumer(request, context.personaByMemberId())
                    );
                })
                .toList();
        List<DevMatchingActionContext.PaymentState> paymentStates = context.payments().stream()
                .map(payment -> new DevMatchingActionContext.PaymentState(
                        payment.getId(),
                        payment.getMatchingRequest().getId(),
                        payment.getStatus()
                ))
                .toList();
        return new DevMatchingActionContext(
                context.request().getId(),
                id(context.group()),
                context.group() == null ? null : context.group().getStatus(),
                id(context.offer()),
                context.offer() == null ? null : context.offer().getStatus(),
                instructor(context.offer(), context.personaByMemberId()),
                requestStates,
                paymentStates
        );
    }

    private List<DevMatchingPersonResponse> people(CaseContext context) {
        LinkedHashMap<String, DevMatchingPersonResponse> people = new LinkedHashMap<>();
        context.relatedRequests().stream()
                .sorted(Comparator.comparing(MatchingRequest::getId))
                .map(request -> consumer(request, context.personaByMemberId()))
                .forEach(person -> people.put(person.personRole() + ":" + person.memberId(), person));
        DevMatchingPersonResponse instructor = instructor(context.offer(), context.personaByMemberId());
        if (instructor != null) {
            people.put(instructor.personRole() + ":" + instructor.memberId(), instructor);
        }
        return List.copyOf(people.values());
    }

    private DevMatchingPersonResponse consumer(
            MatchingRequest request,
            Map<Long, DevPersona> personaByMemberId
    ) {
        Member member = request.getMember();
        DevPersona persona = personaByMemberId.get(member.getId());
        return new DevMatchingPersonResponse(
                DevMatchingPersonRole.CONSUMER,
                member.getId(),
                null,
                persona == null ? null : persona.getPersonaKey(),
                member.getNickname()
        );
    }

    private DevMatchingPersonResponse instructor(
            MatchingOffer offer,
            Map<Long, DevPersona> personaByMemberId
    ) {
        if (offer == null) {
            return null;
        }
        Member member = offer.getInstructorProfile().getMember();
        DevPersona persona = personaByMemberId.get(member.getId());
        return new DevMatchingPersonResponse(
                DevMatchingPersonRole.INSTRUCTOR,
                member.getId(),
                offer.getInstructorProfile().getId(),
                persona == null ? null : persona.getPersonaKey(),
                member.getNickname()
        );
    }

    private List<DevMatchingParticipantResponse> participants(CaseContext context) {
        return context.participants().stream()
                .map(participant -> new DevMatchingParticipantResponse(
                        participant.getId(),
                        participant.getMatchingRequest().getId(),
                        participant.getName(),
                        participant.getAge(),
                        participant.getGender()
                ))
                .toList();
    }

    private List<DevMatchingRequestRelationResponse> requestRelations(CaseContext context) {
        Map<Long, MatchingRequestGroupItem> itemByRequestId = context.groupItems().stream()
                .collect(Collectors.toMap(
                        item -> item.getMatchingRequest().getId(),
                        Function.identity()
                ));
        return context.relatedRequests().stream()
                .sorted(Comparator.comparing(MatchingRequest::getId))
                .map(request -> {
                    MatchingRequestGroupItem item = itemByRequestId.get(request.getId());
                    if (item == null && request.getId().equals(context.request().getId())) {
                        item = context.currentItem();
                    }
                    MatchingRequestPayment payment = findPayment(context.payments(), request.getId());
                    return new DevMatchingRequestRelationResponse(
                            request.getId(),
                            request.getMember().getId(),
                            id(item),
                            id(context.group()),
                            id(context.offer()),
                            payment == null ? null : payment.getId(),
                            context.statusesByRequestId().get(request.getId())
                    );
                })
                .toList();
    }

    private List<DevMatchingResourceResponse> resources(CaseContext context) {
        LinkedHashMap<String, DevMatchingResourceResponse> resources = new LinkedHashMap<>();
        for (MatchingRequest request : context.relatedRequests()) {
            addResource(resources, resource(
                    DevMatchingResourceType.MATCHING_REQUEST,
                    request.getId(),
                    request.getStatus(),
                    request.getStatusReason(),
                    request.getCreatedAt(),
                    request.getUpdatedAt()
            ));
        }
        if (context.group() != null) {
            addResource(resources, resource(
                    DevMatchingResourceType.MATCHING_REQUEST_GROUP,
                    context.group().getId(),
                    context.group().getStatus(),
                    null,
                    context.group().getCreatedAt(),
                    context.group().getUpdatedAt()
            ));
        }
        for (MatchingRequestGroupItem item : context.groupItems()) {
            addResource(resources, resource(
                    DevMatchingResourceType.MATCHING_REQUEST_GROUP_ITEM,
                    item.getId(),
                    item.getStatus(),
                    null,
                    item.getCreatedAt(),
                    item.getUpdatedAt()
            ));
        }
        if (context.currentItem() != null) {
            addResource(resources, resource(
                    DevMatchingResourceType.MATCHING_REQUEST_GROUP_ITEM,
                    context.currentItem().getId(),
                    context.currentItem().getStatus(),
                    null,
                    context.currentItem().getCreatedAt(),
                    context.currentItem().getUpdatedAt()
            ));
        }
        if (context.offer() != null) {
            addResource(resources, resource(
                    DevMatchingResourceType.MATCHING_OFFER,
                    context.offer().getId(),
                    context.offer().getStatus(),
                    null,
                    context.offer().getCreatedAt(),
                    context.offer().getUpdatedAt()
            ));
        }
        for (MatchingRequestPayment payment : context.payments()) {
            addResource(resources, resource(
                    DevMatchingResourceType.MATCHING_REQUEST_PAYMENT,
                    payment.getId(),
                    payment.getStatus(),
                    null,
                    payment.getCreatedAt(),
                    payment.getUpdatedAt()
            ));
        }
        if (context.lesson() != null) {
            addResource(resources, resource(
                    DevMatchingResourceType.LESSON,
                    context.lesson().getId(),
                    context.lesson().getStatus(),
                    null,
                    context.lesson().getCreatedAt(),
                    context.lesson().getUpdatedAt()
            ));
        }
        return resources.values().stream()
                .sorted(Comparator
                        .comparing(DevMatchingResourceResponse::resourceType)
                        .thenComparing(
                                DevMatchingResourceResponse::resourceId,
                                Comparator.nullsLast(Comparator.naturalOrder())
                        ))
                .toList();
    }

    private void addResource(
            Map<String, DevMatchingResourceResponse> resources,
            DevMatchingResourceResponse resource
    ) {
        resources.put(resource.resourceType() + ":" + resource.resourceId(), resource);
    }

    private DevMatchingResourceResponse resource(
            DevMatchingResourceType type,
            Long id,
            Enum<?> status,
            Enum<?> statusReason,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new DevMatchingResourceResponse(
                type,
                id,
                status == null ? null : status.name(),
                statusReason == null ? null : statusReason.name(),
                createdAt,
                updatedAt
        );
    }

    private MatchingOffer resolveCurrentOffer(
            MatchingRequest request,
            MatchingRequestGroup group,
            List<MatchingOffer> groupOffers
    ) {
        MatchingOffer requestOffer = request.getMatchingOffer();
        if (group == null) {
            return requestOffer;
        }
        if (requestOffer != null
                && Objects.equals(requestOffer.getMatchingRequestGroup().getId(), group.getId())) {
            return requestOffer;
        }
        return groupOffers.stream()
                .filter(this::isLiveOffer)
                .findFirst()
                .or(() -> groupOffers.stream().findFirst())
                .orElse(null);
    }

    private Map<Long, List<MatchingRequestGroupItem>> groupItemsByRequestId(
            List<MatchingRequestGroupItem> items
    ) {
        return items.stream()
                .collect(Collectors.groupingBy(
                        item -> item.getMatchingRequest().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    private Map<Long, MatchingRequestGroupItem> currentItems(
            Map<Long, List<MatchingRequestGroupItem>> itemHistoryByRequestId
    ) {
        Map<Long, MatchingRequestGroupItem> currentItems = new LinkedHashMap<>();
        itemHistoryByRequestId.forEach((requestId, history) -> {
            MatchingRequestGroupItem currentItem = selectCurrentItem(history);
            if (currentItem != null) {
                currentItems.put(requestId, currentItem);
            }
        });
        return currentItems;
    }

    private MatchingRequestGroupItem selectCurrentItem(List<MatchingRequestGroupItem> history) {
        // 더 큰 ID의 종료 이력이 살아 있는 협상을 가리지 않도록 활성 그룹을 먼저 선택한다.
        Comparator<MatchingRequestGroupItem> newestFirst = Comparator.comparing(
                MatchingRequestGroupItem::getId,
                Comparator.reverseOrder()
        );
        return history.stream()
                .filter(item -> isActiveNegotiationGroup(item.getMatchingRequestGroup().getStatus()))
                .max(Comparator.comparing(MatchingRequestGroupItem::getId))
                .or(() -> history.stream().sorted(newestFirst).findFirst())
                .orElse(null);
    }

    private boolean isLiveOffer(MatchingOffer offer) {
        if (offer.getStatus() == MatchingOfferStatus.OFFERED) {
            return true;
        }
        return offer.getStatus() == MatchingOfferStatus.ACCEPTED
                && liveAcceptedGroupStatuses().contains(offer.getMatchingRequestGroup().getStatus());
    }

    private Set<MatchingRequestGroupStatus> liveAcceptedGroupStatuses() {
        return Set.of(
                MatchingRequestGroupStatus.INSTRUCTOR_ACCEPTED,
                MatchingRequestGroupStatus.CONSUMER_ACCEPTED,
                MatchingRequestGroupStatus.PAYMENT_PENDING
        );
    }

    private MatchingRequestPayment findPayment(
            List<MatchingRequestPayment> payments,
            Long matchingRequestId
    ) {
        return payments.stream()
                .filter(payment -> payment.getMatchingRequest().getId().equals(matchingRequestId))
                .findFirst()
                .orElse(null);
    }

    private List<MatchingRequest> relatedRequests(
            MatchingRequest selectedRequest,
            List<MatchingRequestGroupItem> groupItems
    ) {
        LinkedHashMap<Long, MatchingRequest> related = new LinkedHashMap<>();
        for (MatchingRequestGroupItem item : groupItems) {
            related.put(item.getMatchingRequest().getId(), item.getMatchingRequest());
        }
        related.putIfAbsent(selectedRequest.getId(), selectedRequest);
        return List.copyOf(related.values());
    }

    private MatchingStatus resolveStatus(
            MatchingRequest request,
            MatchingRequestGroup group,
            MatchingRequestGroupItem item,
            MatchingOffer offer,
            MatchingRequestPayment payment,
            List<String> diagnostics
    ) {
        // 한 요청의 논리적 관계 오류가 전체 dev 목록 500으로 번지지 않도록 해당 case 진단으로 격리한다.
        try {
            return matchingStatusResolver.resolve(
                    request,
                    Optional.ofNullable(group),
                    Optional.ofNullable(item),
                    Optional.ofNullable(offer),
                    Optional.ofNullable(payment)
            );
        } catch (RuntimeException exception) {
            diagnostics.add("matchingRequestId=" + request.getId()
                    + ": matchingStatus 계산 실패 (" + diagnosticCode(exception) + ")");
            return null;
        }
    }

    private void validateActiveGroupCardinality(
            MatchingRequest request,
            List<MatchingRequestGroupItem> history,
            List<String> diagnostics
    ) {
        List<Long> activeGroupIds = history.stream()
                .filter(item -> isActiveNegotiationGroup(item.getMatchingRequestGroup().getStatus()))
                .map(MatchingRequestGroupItem::getMatchingRequestGroup)
                .map(MatchingRequestGroup::getId)
                .distinct()
                .sorted()
                .toList();
        if (activeGroupIds.size() > 1) {
            String groupIds = activeGroupIds.stream()
                    .map(groupId -> "groupId=" + groupId)
                    .collect(Collectors.joining(", "));
            diagnostics.add("matchingRequestId=" + request.getId()
                    + ": 활성 group이 여러 개입니다. " + groupIds);
        }
    }

    private void validateRelatedCurrentGroup(
            MatchingRequest request,
            MatchingRequestGroup displayedGroup,
            MatchingRequestGroupItem actualCurrentItem,
            List<String> diagnostics
    ) {
        if (displayedGroup == null || actualCurrentItem == null) {
            return;
        }
        Long actualGroupId = actualCurrentItem.getMatchingRequestGroup().getId();
        if (!displayedGroup.getId().equals(actualGroupId)) {
            diagnostics.add("matchingRequestId=" + request.getId()
                    + ": 표시 groupId=" + displayedGroup.getId()
                    + "와 현재 groupId=" + actualGroupId + "가 다릅니다.");
        }
    }

    private void validateParticipantCoverage(
            MatchingRequest request,
            List<MatchingRequestParticipant> participants,
            List<String> diagnostics
    ) {
        if (request.getHeadcount() != participants.size()) {
            diagnostics.add("matchingRequestId=" + request.getId()
                    + ": headcount=" + request.getHeadcount()
                    + "와 participantCount=" + participants.size() + "가 다릅니다.");
        }
    }

    private void validateGroupOfferCardinality(
            MatchingRequestGroup group,
            List<MatchingOffer> offers,
            List<String> diagnostics
    ) {
        if (group == null) {
            return;
        }
        List<Long> liveOfferIds = offers.stream()
                .filter(this::isLiveOffer)
                .map(MatchingOffer::getId)
                .sorted()
                .toList();
        if (liveOfferIds.size() > 1) {
            diagnostics.add("groupId=" + group.getId()
                    + ": live offer가 여러 개입니다. offerIds=" + liveOfferIds);
        }
    }

    private void validateInstructorLiveOfferCardinality(
            MatchingOffer currentOffer,
            Map<Long, List<MatchingOffer>> liveOffersByInstructorProfileId,
            List<String> diagnostics
    ) {
        if (currentOffer == null || !isLiveOffer(currentOffer)) {
            return;
        }
        Long instructorProfileId = currentOffer.getInstructorProfile().getId();
        List<MatchingOffer> liveOffers = liveOffersByInstructorProfileId
                .getOrDefault(instructorProfileId, List.of());
        Set<Long> liveGroupIds = liveOffers.stream()
                .map(MatchingOffer::getMatchingRequestGroup)
                .map(MatchingRequestGroup::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (liveGroupIds.size() > 1) {
            List<Long> liveOfferIds = liveOffers.stream().map(MatchingOffer::getId).sorted().toList();
            diagnostics.add("instructorProfileId=" + instructorProfileId
                    + ": 서로 다른 group의 live 협상이 여러 개입니다. groupIds=" + liveGroupIds
                    + ", offerIds=" + liveOfferIds);
        }
    }

    private void validatePriceSnapshot(
            MatchingOffer offer,
            Map<Long, Long> priceSnapshotCountByOfferId,
            List<String> diagnostics
    ) {
        if (offer == null) {
            return;
        }
        long snapshotCount = priceSnapshotCountByOfferId.getOrDefault(offer.getId(), 0L);
        if (snapshotCount != 1) {
            diagnostics.add("offerId=" + offer.getId()
                    + ": offer price snapshot은 1개여야 하지만 " + snapshotCount + "개입니다.");
        }
    }

    private void validateLessonState(
            MatchingRequest request,
            MatchingOffer offer,
            Lesson lesson,
            List<String> diagnostics
    ) {
        Long requestId = request.getId();
        MatchingRequestStatus status = request.getStatus();
        if ((status == MatchingRequestStatus.REQUESTED
                || status == MatchingRequestStatus.GROUPED
                || status == MatchingRequestStatus.MATCHED)
                && lesson != null) {
            diagnostics.add("matchingRequestId=" + requestId + ": 확정 전 요청에 lesson이 존재합니다.");
        }
        if (status == MatchingRequestStatus.CONFIRMED
                && (lesson == null || (lesson.getStatus() != LessonStatus.CONFIRMED
                && lesson.getStatus() != LessonStatus.IN_PROGRESS))) {
            diagnostics.add("matchingRequestId=" + requestId
                    + ": CONFIRMED 요청의 lesson은 CONFIRMED 또는 IN_PROGRESS여야 합니다.");
        }
        if (status == MatchingRequestStatus.COMPLETED
                && (lesson == null || lesson.getStatus() != LessonStatus.COMPLETED)) {
            diagnostics.add("matchingRequestId=" + requestId
                    + ": COMPLETED 요청의 lesson이 COMPLETED가 아닙니다.");
        }
        if (status == MatchingRequestStatus.CANCELED
                && request.getStatusReason() == null
                && (lesson == null || lesson.getStatus() != LessonStatus.CANCELED)) {
            diagnostics.add("matchingRequestId=" + requestId
                    + ": 강습 취소 요청의 lesson이 CANCELED가 아닙니다.");
        }
        if (status == MatchingRequestStatus.CANCELED
                && request.getStatusReason() == MatchingRequestStatusReason.CONSUMER_CANCELED
                && lesson != null) {
            diagnostics.add("matchingRequestId=" + requestId + ": 직접 취소 요청에 lesson이 존재합니다.");
        }
        if (lesson == null) {
            return;
        }
        if (offer == null || !lesson.getMatchingOffer().getId().equals(offer.getId())) {
            diagnostics.add("matchingRequestId=" + requestId + ": lesson의 offer가 현재 offer와 다릅니다.");
        }
        if (offer != null && !lesson.getInstructorProfile().getId().equals(offer.getInstructorProfile().getId())) {
            diagnostics.add("matchingRequestId=" + requestId + ": lesson 강사가 현재 offer 강사와 다릅니다.");
        }
    }

    private void validateLessonHeadcount(
            MatchingRequestGroup group,
            MatchingOffer offer,
            Lesson lesson,
            List<MatchingRequest> relatedRequests,
            List<String> diagnostics
    ) {
        if (lesson == null) {
            return;
        }
        int expectedHeadcount = relatedRequests.stream().mapToInt(MatchingRequest::getHeadcount).sum();
        if (lesson.getTotalHeadcount() != expectedHeadcount) {
            diagnostics.add("lessonId=" + lesson.getId()
                    + ": totalHeadcount=" + lesson.getTotalHeadcount()
                    + "와 요청 합계=" + expectedHeadcount + "가 다릅니다.");
        }
        if (group == null || group.getStatus() != MatchingRequestGroupStatus.CONFIRMED) {
            diagnostics.add("lessonId=" + lesson.getId() + ": lesson이 있지만 group이 CONFIRMED가 아닙니다.");
        }
        if (offer == null || offer.getStatus() != MatchingOfferStatus.ACCEPTED) {
            diagnostics.add("lessonId=" + lesson.getId() + ": lesson이 있지만 offer가 ACCEPTED가 아닙니다.");
        }
    }

    private void validateRelations(
            MatchingRequest request,
            MatchingRequestGroupItem item,
            MatchingRequestGroup group,
            List<MatchingRequestGroupItem> groupItems,
            MatchingOffer offer,
            MatchingRequestPayment payment,
            MatchingStatus matchingStatus,
            List<String> diagnostics
    ) {
        Long requestId = request.getId();
        if (requiresGroup(request.getStatus()) && (item == null || group == null)) {
            diagnostics.add("matchingRequestId=" + requestId + ": 현재 group/item 관계가 없습니다.");
        }
        if (group != null && groupItems.isEmpty()) {
            diagnostics.add("matchingRequestId=" + requestId + ": groupId=" + group.getId() + "에 item이 없습니다.");
        }
        if (group != null && groupItems.stream()
                .noneMatch(groupItem -> groupItem.getMatchingRequest().getId().equals(requestId))) {
            diagnostics.add("matchingRequestId=" + requestId + ": 현재 group의 item 목록에 요청이 없습니다.");
        }
        if (offer != null && group != null
                && !offer.getMatchingRequestGroup().getId().equals(group.getId())) {
            diagnostics.add("matchingRequestId=" + requestId + ": offerId=" + offer.getId()
                    + "가 현재 groupId=" + group.getId() + "와 연결되지 않습니다.");
        }
        if (requiresAcceptedRequestOffer(request.getStatus())
                && !isCurrentAcceptedRequestOffer(request, offer)) {
            diagnostics.add("matchingRequestId=" + requestId
                    + ": 요청의 matchingOffer가 현재 group의 ACCEPTED offer와 일치하지 않습니다.");
        }
        if (payment != null && offer != null
                && !payment.getMatchingOffer().getId().equals(offer.getId())) {
            diagnostics.add("matchingRequestId=" + requestId + ": paymentId=" + payment.getId()
                    + "가 현재 offerId=" + offer.getId() + "와 연결되지 않습니다.");
        }
        validateCalculatedState(request, group, item, offer, payment, matchingStatus, diagnostics);
    }

    private void validateCalculatedState(
            MatchingRequest request,
            MatchingRequestGroup group,
            MatchingRequestGroupItem item,
            MatchingOffer offer,
            MatchingRequestPayment payment,
            MatchingStatus matchingStatus,
            List<String> diagnostics
    ) {
        if (matchingStatus == null) {
            return;
        }
        // 표시 상태가 계산됐더라도 실제 전이 guard와 다른 원본 조합이면 버튼을 숨기고 진단 대상으로 남긴다.
        Long requestId = request.getId();
        boolean valid = switch (matchingStatus) {
            case SEARCHING -> request.getStatus() == MatchingRequestStatus.REQUESTED
                    && request.getStatusReason() == null
                    && group == null
                    && item == null
                    && offer == null
                    && payment == null;
            case WAITING_FOR_TEAM -> (request.getStatus() == MatchingRequestStatus.REQUESTED
                    || request.getStatus() == MatchingRequestStatus.GROUPED)
                    && group != null
                    && group.getStatus() == MatchingRequestGroupStatus.CANDIDATE
                    && item != null
                    && item.getStatus() == MatchingRequestGroupItemStatus.NOT_REQUESTED
                    && offer == null
                    && payment == null;
            case WAITING_FOR_INSTRUCTOR -> request.getStatus() == MatchingRequestStatus.GROUPED
                    && group != null
                    && group.getStatus() == MatchingRequestGroupStatus.EXPOSED
                    && item != null
                    && item.getStatus() == MatchingRequestGroupItemStatus.NOT_REQUESTED
                    && offer != null
                    && offer.getStatus() == MatchingOfferStatus.OFFERED;
            case WAITING_FOR_CONFIRMATION -> request.getStatus() == MatchingRequestStatus.MATCHED
                    && group != null
                    && group.getStatus() == MatchingRequestGroupStatus.INSTRUCTOR_ACCEPTED
                    && item != null
                    && item.getStatus() == MatchingRequestGroupItemStatus.PENDING
                    && offer != null
                    && offer.getStatus() == MatchingOfferStatus.ACCEPTED;
            case WAITING_FOR_OTHER_CONFIRMATIONS -> request.getStatus() == MatchingRequestStatus.MATCHED
                    && group != null
                    && group.getStatus() == MatchingRequestGroupStatus.INSTRUCTOR_ACCEPTED
                    && item != null
                    && item.getStatus() == MatchingRequestGroupItemStatus.ACCEPTED
                    && offer != null
                    && offer.getStatus() == MatchingOfferStatus.ACCEPTED;
            case PAYMENT_PENDING -> request.getStatus() == MatchingRequestStatus.MATCHED
                    && group != null
                    && group.getStatus() == MatchingRequestGroupStatus.PAYMENT_PENDING
                    && item != null
                    && item.getStatus() == MatchingRequestGroupItemStatus.ACCEPTED
                    && offer != null
                    && offer.getStatus() == MatchingOfferStatus.ACCEPTED
                    && payment != null
                    && payment.getStatus() == MatchingRequestPaymentStatus.PENDING;
            case WAITING_FOR_OTHER_PAYMENTS -> request.getStatus() == MatchingRequestStatus.MATCHED
                    && group != null
                    && group.getStatus() == MatchingRequestGroupStatus.PAYMENT_PENDING
                    && item != null
                    && item.getStatus() == MatchingRequestGroupItemStatus.ACCEPTED
                    && offer != null
                    && offer.getStatus() == MatchingOfferStatus.ACCEPTED
                    && payment != null
                    && payment.getStatus() == MatchingRequestPaymentStatus.COMPLETED;
            case PAYMENT_EXPIRED -> isValidPaymentExpiredState(request, group, item, offer, payment);
            case REMATCHING -> isRematchingState(request)
                    && isClosedOrAbsentNegotiation(group, offer, false);
            case CONFIRMED -> (request.getStatus() == MatchingRequestStatus.CONFIRMED
                    || request.getStatus() == MatchingRequestStatus.COMPLETED)
                    && group != null
                    && group.getStatus() == MatchingRequestGroupStatus.CONFIRMED
                    && item != null
                    && item.getStatus() == MatchingRequestGroupItemStatus.ACCEPTED
                    && offer != null
                    && offer.getStatus() == MatchingOfferStatus.ACCEPTED
                    && payment != null
                    && payment.getStatus() == MatchingRequestPaymentStatus.COMPLETED;
            case CANCELED -> request.getStatus() == MatchingRequestStatus.CANCELED
                    && isClosedOrAbsentNegotiation(group, offer, true);
            case NO_AVAILABLE_INSTRUCTOR -> request.getStatus() == MatchingRequestStatus.FAILED
                    && request.getStatusReason() == MatchingRequestStatusReason.NO_AVAILABLE_INSTRUCTOR
                    && isClosedOrAbsentNegotiation(group, offer, false);
            case FAILED -> isFailedState(request)
                    && isClosedOrAbsentNegotiation(group, offer, false);
        };
        if (!valid) {
            diagnostics.add("matchingRequestId=" + requestId + ": 계산된 " + matchingStatus
                    + "와 원본 관계 상태가 일치하지 않습니다.");
        }
    }

    private boolean requiresAcceptedRequestOffer(MatchingRequestStatus status) {
        return status == MatchingRequestStatus.MATCHED
                || status == MatchingRequestStatus.CONFIRMED
                || status == MatchingRequestStatus.COMPLETED;
    }

    private boolean isCurrentAcceptedRequestOffer(
            MatchingRequest request,
            MatchingOffer currentOffer
    ) {
        MatchingOffer requestOffer = request.getMatchingOffer();
        return requestOffer != null
                && currentOffer != null
                && requestOffer.getId().equals(currentOffer.getId())
                && requestOffer.getStatus() == MatchingOfferStatus.ACCEPTED;
    }

    private boolean isValidPaymentExpiredState(
            MatchingRequest request,
            MatchingRequestGroup group,
            MatchingRequestGroupItem item,
            MatchingOffer offer,
            MatchingRequestPayment payment
    ) {
        boolean hasExpirationSource = request.getStatus() == MatchingRequestStatus.EXPIRED
                && request.getStatusReason() == MatchingRequestStatusReason.PAYMENT_TIMEOUT;
        hasExpirationSource = hasExpirationSource
                || group != null && group.getStatus() == MatchingRequestGroupStatus.PAYMENT_EXPIRED;
        hasExpirationSource = hasExpirationSource
                || payment != null && payment.getStatus() == MatchingRequestPaymentStatus.EXPIRED;
        return hasExpirationSource
                && group != null
                && item != null
                && item.getStatus() == MatchingRequestGroupItemStatus.ACCEPTED
                && offer != null
                && offer.getStatus() == MatchingOfferStatus.ACCEPTED
                && isCurrentAcceptedRequestOffer(request, offer)
                && payment != null;
    }

    private boolean isRematchingState(MatchingRequest request) {
        return (request.getStatus() == MatchingRequestStatus.REQUESTED
                || request.getStatus() == MatchingRequestStatus.EXPIRED)
                && isRematchingReason(request.getStatusReason());
    }

    private boolean isClosedOrAbsentNegotiation(
            MatchingRequestGroup group,
            MatchingOffer offer,
            boolean allowConfirmedHistory
    ) {
        if (group != null && isActiveNegotiationGroup(group.getStatus())) {
            return false;
        }
        if (offer == null) {
            return true;
        }
        if (offer.getStatus() == MatchingOfferStatus.OFFERED) {
            return false;
        }
        if (offer.getStatus() != MatchingOfferStatus.ACCEPTED) {
            return true;
        }
        // 강습 확정 뒤 취소는 확정 당시의 ACCEPTED offer를 이력으로 유지한다.
        return allowConfirmedHistory
                && group != null
                && group.getStatus() == MatchingRequestGroupStatus.CONFIRMED;
    }

    private boolean isActiveNegotiationGroup(MatchingRequestGroupStatus status) {
        return status == MatchingRequestGroupStatus.CANDIDATE
                || status == MatchingRequestGroupStatus.EXPOSED
                || status == MatchingRequestGroupStatus.INSTRUCTOR_ACCEPTED
                || status == MatchingRequestGroupStatus.CONSUMER_ACCEPTED
                || status == MatchingRequestGroupStatus.PAYMENT_PENDING;
    }

    private boolean isFailedState(MatchingRequest request) {
        if (request.getStatus() == MatchingRequestStatus.FAILED) {
            return request.getStatusReason() != MatchingRequestStatusReason.NO_AVAILABLE_INSTRUCTOR;
        }
        return request.getStatus() == MatchingRequestStatus.EXPIRED
                && request.getStatusReason() != MatchingRequestStatusReason.PAYMENT_TIMEOUT
                && !isRematchingReason(request.getStatusReason());
    }

    private boolean isRematchingReason(MatchingRequestStatusReason reason) {
        return reason == MatchingRequestStatusReason.CONSUMER_REJECTED_INSTRUCTOR
                || reason == MatchingRequestStatusReason.INSTRUCTOR_REJECTED
                || reason == MatchingRequestStatusReason.INSTRUCTOR_TIMEOUT
                || reason == MatchingRequestStatusReason.CONFIRMATION_TIMEOUT
                || reason == MatchingRequestStatusReason.GROUP_CANCELED;
    }

    private void validatePaymentCoverage(
            MatchingRequestGroup group,
            List<MatchingRequestGroupItem> groupItems,
            List<MatchingRequestPayment> payments,
            List<String> diagnostics
    ) {
        if (group == null) {
            return;
        }
        MatchingRequestGroupStatus groupStatus = group.getStatus();
        boolean beforePayment = groupStatus == MatchingRequestGroupStatus.CANDIDATE
                || groupStatus == MatchingRequestGroupStatus.EXPOSED
                || groupStatus == MatchingRequestGroupStatus.INSTRUCTOR_ACCEPTED
                || groupStatus == MatchingRequestGroupStatus.CONSUMER_ACCEPTED;
        if (beforePayment && !payments.isEmpty()) {
            diagnostics.add("groupId=" + group.getId() + ": 결제 전 단계에 payment가 존재합니다.");
            return;
        }
        if (groupStatus != MatchingRequestGroupStatus.PAYMENT_PENDING
                && groupStatus != MatchingRequestGroupStatus.CONFIRMED) {
            return;
        }
        Set<Long> itemRequestIds = groupItems.stream()
                .map(item -> item.getMatchingRequest().getId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> paymentRequestIds = payments.stream()
                .map(payment -> payment.getMatchingRequest().getId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!itemRequestIds.equals(paymentRequestIds)) {
            diagnostics.add("groupId=" + group.getId()
                    + ": group item 요청과 payment 요청 구성이 일치하지 않습니다.");
        }
        if (paymentRequestIds.size() != payments.size()) {
            diagnostics.add("groupId=" + group.getId() + ": 같은 요청의 payment가 여러 개입니다.");
        }
        if (groupStatus == MatchingRequestGroupStatus.CONFIRMED
                && payments.stream().anyMatch(payment -> payment.getStatus() != MatchingRequestPaymentStatus.COMPLETED)) {
            diagnostics.add("groupId=" + group.getId() + ": CONFIRMED 그룹의 payment가 모두 COMPLETED가 아닙니다.");
        }
    }

    private boolean requiresGroup(MatchingRequestStatus status) {
        return status == MatchingRequestStatus.GROUPED
                || status == MatchingRequestStatus.MATCHED
                || status == MatchingRequestStatus.CONFIRMED
                || status == MatchingRequestStatus.COMPLETED;
    }

    private List<String> actionLimitations(CaseContext context) {
        List<String> limitations = new ArrayList<>();
        if (context.groupItems().size() > 1) {
            limitations.add("다중 요청 그룹의 결제 분담 정책은 아직 지원되지 않아 강습생 수락 미리보기를 숨깁니다.");
        }
        if (context.offer() != null && context.offer().getStatus() == MatchingOfferStatus.OFFERED) {
            limitations.add("강사 거절 결과는 다음 후보 존재 여부에 따라 두 가지로 나뉩니다.");
        }
        limitations.add("stateToken은 #159의 실행 직전 잠금 재검증에 사용할 조회 스냅샷이며, 이 API는 상태를 변경하지 않습니다.");
        return List.copyOf(limitations);
    }

    private String diagnosticCode(RuntimeException exception) {
        if (exception instanceof BusinessException businessException) {
            return businessException.getErrorCode().getCode();
        }
        return exception.getClass().getSimpleName();
    }

    private Long id(MatchingRequestGroup group) {
        return group == null ? null : group.getId();
    }

    private Long id(MatchingOffer offer) {
        return offer == null ? null : offer.getId();
    }

    private Long id(MatchingRequestGroupItem item) {
        return item == null ? null : item.getId();
    }

    private record CaseContext(
            MatchingRequest request,
            MatchingRequestGroupItem currentItem,
            MatchingRequestGroup group,
            MatchingOffer offer,
            MatchingRequestPayment payment,
            Lesson lesson,
            List<MatchingRequestGroupItem> groupItems,
            List<MatchingRequestPayment> payments,
            List<MatchingRequestParticipant> participants,
            Map<Long, DevPersona> personaByMemberId,
            List<MatchingRequest> relatedRequests,
            Map<Long, MatchingStatus> statusesByRequestId,
            MatchingStatus matchingStatus,
            DevMatchingResolutionState resolutionState,
            List<String> diagnostics
    ) {
    }
}
