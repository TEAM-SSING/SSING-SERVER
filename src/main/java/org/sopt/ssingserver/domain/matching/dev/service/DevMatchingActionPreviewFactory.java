package org.sopt.ssingserver.domain.matching.dev.service;

import static org.sopt.ssingserver.domain.matching.dev.enums.DevMatchingResourceType.LESSON;
import static org.sopt.ssingserver.domain.matching.dev.enums.DevMatchingResourceType.MATCHING_OFFER;
import static org.sopt.ssingserver.domain.matching.dev.enums.DevMatchingResourceType.MATCHING_REQUEST;
import static org.sopt.ssingserver.domain.matching.dev.enums.DevMatchingResourceType.MATCHING_REQUEST_GROUP;
import static org.sopt.ssingserver.domain.matching.dev.enums.DevMatchingResourceType.MATCHING_REQUEST_GROUP_ITEM;
import static org.sopt.ssingserver.domain.matching.dev.enums.DevMatchingResourceType.MATCHING_REQUEST_PAYMENT;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.sopt.ssingserver.domain.matching.dev.dto.response.DevMatchingActionPreviewResponse;
import org.sopt.ssingserver.domain.matching.dev.dto.response.DevMatchingActionPreviewResponse.AffectedResource;
import org.sopt.ssingserver.domain.matching.dev.dto.response.DevMatchingActionPreviewResponse.Outcome;
import org.sopt.ssingserver.domain.matching.dev.dto.response.DevMatchingActionPreviewResponse.PersonStatusChange;
import org.sopt.ssingserver.domain.matching.dev.dto.response.DevMatchingActionPreviewResponse.ResourceStateChange;
import org.sopt.ssingserver.domain.matching.dev.dto.response.DevMatchingPersonResponse;
import org.sopt.ssingserver.domain.matching.dev.enums.DevMatchingActionKey;
import org.sopt.ssingserver.domain.matching.dev.enums.DevMatchingResourceType;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupItemStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatusReason;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
import org.sopt.ssingserver.domain.payment.enums.MatchingRequestPaymentStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile({"local", "dev"})
@Component
@RequiredArgsConstructor
class DevMatchingActionPreviewFactory {

    private final DevMatchingActionPolicy actionPolicy;

    // 실제 전이 메서드를 호출하지 않고 production guard와 같은 원본 상태 조합에서만 미리보기를 만든다.
    List<DevMatchingActionPreviewResponse> create(DevMatchingActionContext context) {
        List<DevMatchingActionPreviewResponse> actions = new ArrayList<>();
        if (isInstructorRespondable(context)) {
            actions.add(instructorAccept(context));
            actions.add(instructorReject(context));
        }
        if (isConsumerConfirmable(context)) {
            if (context.requests().size() == 1) {
                actions.add(consumerAccept(context));
            }
            actions.add(consumerReject(context));
        }
        if (isPaymentCompletable(context)) {
            actions.add(paymentComplete(context));
        }
        return List.copyOf(actions);
    }

    private boolean isInstructorRespondable(DevMatchingActionContext context) {
        return context.groupId() != null
                && context.offerId() != null
                && context.instructor() != null
                && !context.requests().isEmpty()
                && context.groupStatus() == MatchingRequestGroupStatus.EXPOSED
                && context.offerStatus() == MatchingOfferStatus.OFFERED
                && context.requests().stream().allMatch(request ->
                        request.requestStatus() == MatchingRequestStatus.GROUPED
                                && request.groupItemStatus() == MatchingRequestGroupItemStatus.NOT_REQUESTED
                                && request.matchingStatus() == MatchingStatus.WAITING_FOR_INSTRUCTOR
                );
    }

    private boolean isConsumerConfirmable(DevMatchingActionContext context) {
        DevMatchingActionContext.RequestState request = context.selectedRequest();
        return request.requestStatus() == MatchingRequestStatus.MATCHED
                && request.groupItemStatus() == MatchingRequestGroupItemStatus.PENDING
                && context.groupStatus() == MatchingRequestGroupStatus.INSTRUCTOR_ACCEPTED
                && context.offerStatus() == MatchingOfferStatus.ACCEPTED;
    }

    private boolean isPaymentCompletable(DevMatchingActionContext context) {
        DevMatchingActionContext.RequestState request = context.selectedRequest();
        DevMatchingActionContext.PaymentState payment = context.selectedPayment();
        return request.requestStatus() == MatchingRequestStatus.MATCHED
                && request.groupItemStatus() == MatchingRequestGroupItemStatus.ACCEPTED
                && context.groupStatus() == MatchingRequestGroupStatus.PAYMENT_PENDING
                && context.offerStatus() == MatchingOfferStatus.ACCEPTED
                && payment != null
                && payment.paymentStatus() == MatchingRequestPaymentStatus.PENDING;
    }

    private DevMatchingActionPreviewResponse instructorAccept(DevMatchingActionContext context) {
        List<PersonStatusChange> people = context.requests().stream()
                .map(request -> personChange(
                        request.consumer(),
                        request.matchingStatus(),
                        MatchingStatus.WAITING_FOR_CONFIRMATION
                ))
                .toList();
        List<ResourceStateChange> resources = new ArrayList<>();
        resources.add(change(MATCHING_OFFER, context.offerId(), "status",
                context.offerStatus(), MatchingOfferStatus.ACCEPTED));
        resources.add(change(MATCHING_REQUEST_GROUP, context.groupId(), "status",
                context.groupStatus(), MatchingRequestGroupStatus.INSTRUCTOR_ACCEPTED));
        for (DevMatchingActionContext.RequestState request : context.requests()) {
            resources.add(change(MATCHING_REQUEST, request.matchingRequestId(), "status",
                    request.requestStatus(), MatchingRequestStatus.MATCHED));
            resources.add(change(MATCHING_REQUEST_GROUP_ITEM, request.groupItemId(), "status",
                    request.groupItemStatus(), MatchingRequestGroupItemStatus.PENDING));
        }
        Outcome outcome = outcome(
                "ACCEPTED",
                "현재 제안을 강사가 수락한 경우",
                false,
                people,
                resources,
                "그룹의 모든 강습생이 최종 확인 단계로 이동합니다."
        );
        return action(
                context,
                DevMatchingActionKey.INSTRUCTOR_ACCEPT,
                "강사 수락",
                context.instructor(),
                withInstructor(context),
                List.of(outcome)
        );
    }

    private DevMatchingActionPreviewResponse instructorReject(DevMatchingActionContext context) {
        List<ResourceStateChange> common = List.of(change(
                MATCHING_OFFER,
                context.offerId(),
                "status",
                context.offerStatus(),
                MatchingOfferStatus.REJECTED
        ));

        List<ResourceStateChange> nextInstructorResources = new ArrayList<>(common);
        nextInstructorResources.add(change(
                MATCHING_OFFER,
                null,
                "status",
                "ABSENT",
                MatchingOfferStatus.OFFERED
        ));
        Outcome nextInstructor = outcome(
                "NEXT_INSTRUCTOR_AVAILABLE",
                "다음 조건에 맞는 강사가 있는 경우",
                true,
                List.of(),
                nextInstructorResources,
                "새 offer ID와 다음 강사는 실행 시점에 결정됩니다."
        );

        List<ResourceStateChange> noInstructorResources = new ArrayList<>(common);
        noInstructorResources.add(change(MATCHING_REQUEST_GROUP, context.groupId(), "status",
                context.groupStatus(), MatchingRequestGroupStatus.CANCELED));
        for (DevMatchingActionContext.RequestState request : context.requests()) {
            noInstructorResources.add(change(MATCHING_REQUEST, request.matchingRequestId(), "status",
                    request.requestStatus(), MatchingRequestStatus.REQUESTED));
            noInstructorResources.add(change(MATCHING_REQUEST, request.matchingRequestId(), "statusReason",
                    null, MatchingRequestStatusReason.INSTRUCTOR_REJECTED));
        }
        List<PersonStatusChange> rematchingPeople = context.requests().stream()
                .map(request -> personChange(request.consumer(), request.matchingStatus(), MatchingStatus.REMATCHING))
                .toList();
        Outcome noInstructor = outcome(
                "NO_NEXT_INSTRUCTOR",
                "다음 조건에 맞는 강사가 없는 경우",
                true,
                rematchingPeople,
                noInstructorResources,
                "현재 그룹을 닫고 같은 요청으로 다시 탐색합니다."
        );

        return action(
                context,
                DevMatchingActionKey.INSTRUCTOR_REJECT,
                "강사 거절",
                context.instructor(),
                withInstructor(context),
                List.of(nextInstructor, noInstructor)
        );
    }

    private DevMatchingActionPreviewResponse consumerAccept(DevMatchingActionContext context) {
        DevMatchingActionContext.RequestState request = context.selectedRequest();
        List<ResourceStateChange> resources = List.of(
                change(MATCHING_REQUEST_GROUP_ITEM, request.groupItemId(), "status",
                        request.groupItemStatus(), MatchingRequestGroupItemStatus.ACCEPTED),
                change(MATCHING_REQUEST_GROUP, context.groupId(), "status",
                        context.groupStatus(), MatchingRequestGroupStatus.PAYMENT_PENDING),
                change(MATCHING_REQUEST_PAYMENT, null, "status",
                        "ABSENT", MatchingRequestPaymentStatus.PENDING)
        );
        Outcome outcome = outcome(
                "PAYMENT_PENDING",
                "강습생이 최종 수락한 경우",
                false,
                List.of(personChange(request.consumer(), request.matchingStatus(), MatchingStatus.PAYMENT_PENDING)),
                resources,
                "결제 row의 ID는 실행 뒤 생성됩니다."
        );
        return action(
                context,
                DevMatchingActionKey.CONSUMER_ACCEPT,
                "강습생 수락",
                request.consumer(),
                context.requests().stream().map(DevMatchingActionContext.RequestState::consumer).toList(),
                List.of(outcome)
        );
    }

    private DevMatchingActionPreviewResponse consumerReject(DevMatchingActionContext context) {
        DevMatchingActionContext.RequestState selected = context.selectedRequest();
        List<ResourceStateChange> resources = new ArrayList<>();
        resources.add(change(MATCHING_OFFER, context.offerId(), "status",
                context.offerStatus(), MatchingOfferStatus.CANCELED));
        resources.add(change(MATCHING_REQUEST_GROUP, context.groupId(), "status",
                context.groupStatus(), MatchingRequestGroupStatus.CANCELED));
        for (DevMatchingActionContext.RequestState request : context.requests()) {
            MatchingRequestGroupItemStatus afterItemStatus = request.matchingRequestId().equals(selected.matchingRequestId())
                    ? MatchingRequestGroupItemStatus.REJECTED
                    : MatchingRequestGroupItemStatus.CANCELED;
            resources.add(change(MATCHING_REQUEST_GROUP_ITEM, request.groupItemId(), "status",
                    request.groupItemStatus(), afterItemStatus));
            resources.add(change(MATCHING_REQUEST, request.matchingRequestId(), "status",
                    request.requestStatus(), MatchingRequestStatus.REQUESTED));
            resources.add(change(MATCHING_REQUEST, request.matchingRequestId(), "statusReason",
                    null, MatchingRequestStatusReason.CONSUMER_REJECTED_INSTRUCTOR));
        }
        List<PersonStatusChange> people = context.requests().stream()
                .map(request -> personChange(request.consumer(), request.matchingStatus(), MatchingStatus.REMATCHING))
                .toList();
        Outcome outcome = outcome(
                "REMATCHING",
                "강습생이 제안을 거절한 경우",
                false,
                people,
                resources,
                "현재 그룹 전체를 닫고 각 요청을 재탐색 상태로 돌립니다."
        );
        return action(
                context,
                DevMatchingActionKey.CONSUMER_REJECT,
                "강습생 거절",
                selected.consumer(),
                withInstructor(context),
                List.of(outcome)
        );
    }

    private DevMatchingActionPreviewResponse paymentComplete(DevMatchingActionContext context) {
        DevMatchingActionContext.RequestState selected = context.selectedRequest();
        DevMatchingActionContext.PaymentState selectedPayment = context.selectedPayment();
        boolean lastPayment = context.payments().stream()
                .filter(payment -> !payment.matchingRequestId().equals(context.selectedRequestId()))
                .allMatch(payment -> payment.paymentStatus() == MatchingRequestPaymentStatus.COMPLETED);

        List<ResourceStateChange> resources = new ArrayList<>();
        resources.add(change(MATCHING_REQUEST_PAYMENT, selectedPayment.paymentId(), "status",
                selectedPayment.paymentStatus(), MatchingRequestPaymentStatus.COMPLETED));
        List<PersonStatusChange> people;
        String outcomeKey;
        String note;
        if (lastPayment) {
            resources.add(change(MATCHING_REQUEST_GROUP, context.groupId(), "status",
                    context.groupStatus(), MatchingRequestGroupStatus.CONFIRMED));
            for (DevMatchingActionContext.RequestState request : context.requests()) {
                resources.add(change(MATCHING_REQUEST, request.matchingRequestId(), "status",
                        request.requestStatus(), MatchingRequestStatus.CONFIRMED));
            }
            resources.add(change(LESSON, null, "status", "ABSENT", "CONFIRMED"));
            people = context.requests().stream()
                    .map(request -> personChange(request.consumer(), request.matchingStatus(), MatchingStatus.CONFIRMED))
                    .toList();
            outcomeKey = "MATCHING_CONFIRMED";
            note = "마지막 결제라면 새 lesson ID가 생성됩니다.";
        } else {
            people = List.of(personChange(
                    selected.consumer(),
                    selected.matchingStatus(),
                    MatchingStatus.WAITING_FOR_OTHER_PAYMENTS
            ));
            outcomeKey = "WAITING_FOR_OTHER_PAYMENTS";
            note = "다른 강습생의 결제가 끝날 때까지 그룹은 결제 대기를 유지합니다.";
        }
        Outcome outcome = outcome(
                outcomeKey,
                lastPayment ? "현재 결제가 그룹의 마지막 미결제인 경우" : "다른 미결제 강습생이 남은 경우",
                context.payments().size() > 1,
                people,
                resources,
                note
        );
        return action(
                context,
                DevMatchingActionKey.PAYMENT_COMPLETE,
                "결제 완료",
                selected.consumer(),
                withInstructor(context),
                List.of(outcome)
        );
    }

    private DevMatchingActionPreviewResponse action(
            DevMatchingActionContext context,
            DevMatchingActionKey key,
            String label,
            DevMatchingPersonResponse actor,
            List<DevMatchingPersonResponse> affectedPeople,
            List<Outcome> outcomes
    ) {
        List<AffectedResource> affectedResources = outcomes.stream()
                .flatMap(outcome -> outcome.resourceStateChanges().stream())
                .map(ResourceStateChange::resource)
                .distinct()
                .toList();
        return new DevMatchingActionPreviewResponse(
                key,
                label,
                actor,
                List.copyOf(new LinkedHashSet<>(affectedPeople)),
                affectedResources,
                outcomes,
                !actionPolicy.isExecutable(context, key)
        );
    }

    private List<DevMatchingPersonResponse> withInstructor(DevMatchingActionContext context) {
        List<DevMatchingPersonResponse> people = new ArrayList<>();
        people.add(context.instructor());
        people.addAll(context.requests().stream().map(DevMatchingActionContext.RequestState::consumer).toList());
        return people;
    }

    private Outcome outcome(
            String key,
            String condition,
            boolean conditional,
            List<PersonStatusChange> people,
            List<ResourceStateChange> resources,
            String note
    ) {
        return new Outcome(key, condition, conditional, people, resources, note);
    }

    private PersonStatusChange personChange(
            DevMatchingPersonResponse person,
            MatchingStatus before,
            MatchingStatus after
    ) {
        return new PersonStatusChange(person, before, after);
    }

    private ResourceStateChange change(
            DevMatchingResourceType type,
            Long id,
            String field,
            Object before,
            Object after
    ) {
        return new ResourceStateChange(
                new AffectedResource(type, id),
                field,
                value(before),
                value(after)
        );
    }

    private String value(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        return value.toString();
    }
}
