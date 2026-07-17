package org.sopt.ssingserver.domain.matching.dev.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.matching.dev.dto.response.DevMatchingActionPreviewResponse;
import org.sopt.ssingserver.domain.matching.dev.dto.response.DevMatchingPersonResponse;
import org.sopt.ssingserver.domain.matching.dev.enums.DevMatchingActionKey;
import org.sopt.ssingserver.domain.matching.dev.enums.DevMatchingPersonRole;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupItemStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestGroupStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingRequestStatus;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
import org.sopt.ssingserver.domain.payment.enums.MatchingRequestPaymentStatus;

class DevMatchingActionPreviewFactoryTest {

    private final DevMatchingActionPreviewFactory factory = new DevMatchingActionPreviewFactory();

    @Test
    void 강사응답대기는_수락과_조건부거절_영향을_반환한다() {
        DevMatchingActionContext context = new DevMatchingActionContext(
                301L,
                98L,
                MatchingRequestGroupStatus.EXPOSED,
                77L,
                MatchingOfferStatus.OFFERED,
                instructor(),
                List.of(request(
                        301L,
                        MatchingRequestStatus.GROUPED,
                        302L,
                        MatchingRequestGroupItemStatus.NOT_REQUESTED,
                        MatchingStatus.WAITING_FOR_INSTRUCTOR,
                        consumer(12L, "consumer-a")
                )),
                List.of()
        );

        List<DevMatchingActionPreviewResponse> actions = factory.create(context);

        assertThat(actions).extracting(DevMatchingActionPreviewResponse::actionKey)
                .containsExactly(DevMatchingActionKey.INSTRUCTOR_ACCEPT, DevMatchingActionKey.INSTRUCTOR_REJECT);
        DevMatchingActionPreviewResponse accept = actions.getFirst();
        assertThat(accept.actor()).isEqualTo(instructor());
        assertThat(memberIds(accept.affectedPeople())).containsExactly(45L, 12L);
        assertThat(resourceKeys(accept)).containsExactly(
                "MATCHING_OFFER#77",
                "MATCHING_REQUEST_GROUP#98",
                "MATCHING_REQUEST#301",
                "MATCHING_REQUEST_GROUP_ITEM#302"
        );
        assertThat(accept.previewOnly()).isTrue();
        assertThat(accept.outcomes()).singleElement()
                .satisfies(outcome -> {
                    assertThat(outcome.outcomeKey()).isEqualTo("ACCEPTED");
                    assertThat(outcome.conditional()).isFalse();
                    assertThat(outcome.personStatusChanges()).singleElement()
                            .satisfies(change -> {
                                assertThat(change.person()).isEqualTo(consumer(12L, "consumer-a"));
                                assertThat(change.before()).isEqualTo(MatchingStatus.WAITING_FOR_INSTRUCTOR);
                                assertThat(change.after()).isEqualTo(MatchingStatus.WAITING_FOR_CONFIRMATION);
                            });
                    assertThat(resourceChangeKeys(outcome)).containsExactly(
                            "MATCHING_OFFER#77.status:OFFERED->ACCEPTED",
                            "MATCHING_REQUEST_GROUP#98.status:EXPOSED->INSTRUCTOR_ACCEPTED",
                            "MATCHING_REQUEST#301.status:GROUPED->MATCHED",
                            "MATCHING_REQUEST_GROUP_ITEM#302.status:NOT_REQUESTED->PENDING"
                    );
                });
        DevMatchingActionPreviewResponse reject = actions.get(1);
        assertThat(reject.actor()).isEqualTo(instructor());
        assertThat(memberIds(reject.affectedPeople())).containsExactly(45L, 12L);
        assertThat(reject.previewOnly()).isTrue();
        assertThat(reject.outcomes())
                .extracting(DevMatchingActionPreviewResponse.Outcome::outcomeKey)
                .containsExactly("NEXT_INSTRUCTOR_AVAILABLE", "NO_NEXT_INSTRUCTOR");
        assertThat(reject.outcomes()).allSatisfy(outcome -> assertThat(outcome.conditional()).isTrue());
        assertThat(resourceChangeKeys(reject.outcomes().getFirst())).containsExactly(
                "MATCHING_OFFER#77.status:OFFERED->REJECTED",
                "MATCHING_OFFER#null.status:ABSENT->OFFERED"
        );
        assertThat(resourceChangeKeys(reject.outcomes().get(1))).containsExactly(
                "MATCHING_OFFER#77.status:OFFERED->REJECTED",
                "MATCHING_REQUEST_GROUP#98.status:EXPOSED->CANCELED",
                "MATCHING_REQUEST#301.status:GROUPED->REQUESTED",
                "MATCHING_REQUEST#301.statusReason:null->INSTRUCTOR_REJECTED"
        );
        assertThat(personChangeKeys(reject.outcomes().get(1))).containsExactly(
                "12:WAITING_FOR_INSTRUCTOR->REMATCHING"
        );
    }

    @Test
    void 단일요청_확인대기는_수락과_거절의_전체영향을_반환한다() {
        DevMatchingPersonResponse consumer = consumer(12L, "consumer-a");
        DevMatchingActionContext context = new DevMatchingActionContext(
                301L,
                98L,
                MatchingRequestGroupStatus.INSTRUCTOR_ACCEPTED,
                77L,
                MatchingOfferStatus.ACCEPTED,
                instructor(),
                List.of(request(
                        301L,
                        MatchingRequestStatus.MATCHED,
                        302L,
                        MatchingRequestGroupItemStatus.PENDING,
                        MatchingStatus.WAITING_FOR_CONFIRMATION,
                        consumer
                )),
                List.of()
        );

        List<DevMatchingActionPreviewResponse> actions = factory.create(context);

        assertThat(actions).extracting(DevMatchingActionPreviewResponse::actionKey)
                .containsExactly(DevMatchingActionKey.CONSUMER_ACCEPT, DevMatchingActionKey.CONSUMER_REJECT);
        DevMatchingActionPreviewResponse accept = actions.getFirst();
        assertThat(accept.actor()).isEqualTo(consumer);
        assertThat(memberIds(accept.affectedPeople())).containsExactly(12L);
        assertThat(resourceKeys(accept)).containsExactly(
                "MATCHING_REQUEST_GROUP_ITEM#302",
                "MATCHING_REQUEST_GROUP#98",
                "MATCHING_REQUEST_PAYMENT#null"
        );
        assertThat(accept.previewOnly()).isTrue();
        assertThat(accept.outcomes()).singleElement().satisfies(outcome -> {
            assertThat(outcome.outcomeKey()).isEqualTo("PAYMENT_PENDING");
            assertThat(outcome.conditional()).isFalse();
            assertThat(resourceChangeKeys(outcome)).containsExactly(
                    "MATCHING_REQUEST_GROUP_ITEM#302.status:PENDING->ACCEPTED",
                    "MATCHING_REQUEST_GROUP#98.status:INSTRUCTOR_ACCEPTED->PAYMENT_PENDING",
                    "MATCHING_REQUEST_PAYMENT#null.status:ABSENT->PENDING"
            );
            assertThat(outcome.personStatusChanges()).singleElement().satisfies(change -> {
                assertThat(change.person()).isEqualTo(consumer);
                assertThat(change.before()).isEqualTo(MatchingStatus.WAITING_FOR_CONFIRMATION);
                assertThat(change.after()).isEqualTo(MatchingStatus.PAYMENT_PENDING);
            });
        });

        DevMatchingActionPreviewResponse reject = actions.get(1);
        assertThat(reject.actor()).isEqualTo(consumer);
        assertThat(memberIds(reject.affectedPeople())).containsExactly(45L, 12L);
        assertThat(reject.previewOnly()).isTrue();
        assertThat(resourceChangeKeys(reject.outcomes().getFirst())).containsExactly(
                "MATCHING_OFFER#77.status:ACCEPTED->CANCELED",
                "MATCHING_REQUEST_GROUP#98.status:INSTRUCTOR_ACCEPTED->CANCELED",
                "MATCHING_REQUEST_GROUP_ITEM#302.status:PENDING->REJECTED",
                "MATCHING_REQUEST#301.status:MATCHED->REQUESTED",
                "MATCHING_REQUEST#301.statusReason:null->CONSUMER_REJECTED_INSTRUCTOR"
        );
        assertThat(personChangeKeys(reject.outcomes().getFirst())).containsExactly(
                "12:WAITING_FOR_CONFIRMATION->REMATCHING"
        );
    }

    @Test
    void 다중요청_확인대기에서는_지원되지_않는_수락을_숨기고_그룹거절만_보여준다() {
        DevMatchingActionContext context = new DevMatchingActionContext(
                301L,
                98L,
                MatchingRequestGroupStatus.INSTRUCTOR_ACCEPTED,
                77L,
                MatchingOfferStatus.ACCEPTED,
                instructor(),
                List.of(
                        request(301L, MatchingRequestStatus.MATCHED, 302L,
                                MatchingRequestGroupItemStatus.PENDING,
                                MatchingStatus.WAITING_FOR_CONFIRMATION,
                                consumer(12L, "consumer-a")),
                        request(303L, MatchingRequestStatus.MATCHED, 304L,
                                MatchingRequestGroupItemStatus.PENDING,
                                MatchingStatus.WAITING_FOR_CONFIRMATION,
                                consumer(13L, "consumer-b"))
                ),
                List.of()
        );

        DevMatchingActionPreviewResponse reject = factory.create(context).getFirst();

        assertThat(List.of(reject))
                .extracting(DevMatchingActionPreviewResponse::actionKey)
                .containsExactly(DevMatchingActionKey.CONSUMER_REJECT);
        assertThat(personChangeKeys(reject.outcomes().getFirst())).containsExactly(
                "12:WAITING_FOR_CONFIRMATION->REMATCHING",
                "13:WAITING_FOR_CONFIRMATION->REMATCHING"
        );
    }

    @Test
    void 그룹과_offer가_응답대기여도_요청상태가_재매칭이면_강사동작을_숨긴다() {
        DevMatchingActionContext context = new DevMatchingActionContext(
                301L,
                98L,
                MatchingRequestGroupStatus.EXPOSED,
                77L,
                MatchingOfferStatus.OFFERED,
                instructor(),
                List.of(request(
                        301L,
                        MatchingRequestStatus.REQUESTED,
                        302L,
                        MatchingRequestGroupItemStatus.NOT_REQUESTED,
                        MatchingStatus.REMATCHING,
                        consumer(12L, "consumer-a")
                )),
                List.of()
        );

        assertThat(factory.create(context)).isEmpty();
    }

    @Test
    void 단일요청_결제대기는_마지막결제의_확정영향을_반환한다() {
        DevMatchingActionContext context = new DevMatchingActionContext(
                301L,
                98L,
                MatchingRequestGroupStatus.PAYMENT_PENDING,
                77L,
                MatchingOfferStatus.ACCEPTED,
                instructor(),
                List.of(request(
                        301L,
                        MatchingRequestStatus.MATCHED,
                        302L,
                        MatchingRequestGroupItemStatus.ACCEPTED,
                        MatchingStatus.PAYMENT_PENDING,
                        consumer(12L, "consumer-a")
                )),
                List.of(new DevMatchingActionContext.PaymentState(
                        401L,
                        301L,
                        MatchingRequestPaymentStatus.PENDING
                ))
        );

        DevMatchingActionPreviewResponse action = factory.create(context).getFirst();

        assertThat(action.actionKey()).isEqualTo(DevMatchingActionKey.PAYMENT_COMPLETE);
        assertThat(action.actor()).isEqualTo(consumer(12L, "consumer-a"));
        assertThat(memberIds(action.affectedPeople())).containsExactly(45L, 12L);
        assertThat(resourceKeys(action)).containsExactly(
                "MATCHING_REQUEST_PAYMENT#401",
                "MATCHING_REQUEST_GROUP#98",
                "MATCHING_REQUEST#301",
                "LESSON#null"
        );
        assertThat(action.previewOnly()).isTrue();
        assertThat(action.outcomes()).singleElement()
                .satisfies(outcome -> {
                    assertThat(outcome.outcomeKey()).isEqualTo("MATCHING_CONFIRMED");
                    assertThat(outcome.conditional()).isFalse();
                    assertThat(outcome.personStatusChanges()).singleElement()
                            .satisfies(change -> {
                                assertThat(change.person()).isEqualTo(consumer(12L, "consumer-a"));
                                assertThat(change.before()).isEqualTo(MatchingStatus.PAYMENT_PENDING);
                                assertThat(change.after()).isEqualTo(MatchingStatus.CONFIRMED);
                            });
                    assertThat(resourceChangeKeys(outcome)).containsExactly(
                            "MATCHING_REQUEST_PAYMENT#401.status:PENDING->COMPLETED",
                            "MATCHING_REQUEST_GROUP#98.status:PAYMENT_PENDING->CONFIRMED",
                            "MATCHING_REQUEST#301.status:MATCHED->CONFIRMED",
                            "LESSON#null.status:ABSENT->CONFIRMED"
                    );
                });
    }

    @Test
    void 탐색중에는_실행가능한_상태동작이_없다() {
        DevMatchingActionContext context = new DevMatchingActionContext(
                301L,
                null,
                null,
                null,
                null,
                null,
                List.of(request(
                        301L,
                        MatchingRequestStatus.REQUESTED,
                        null,
                        null,
                        MatchingStatus.SEARCHING,
                        consumer(12L, "consumer-a")
                )),
                List.of()
        );

        assertThat(factory.create(context)).isEmpty();
    }

    private DevMatchingActionContext.RequestState request(
            Long requestId,
            MatchingRequestStatus requestStatus,
            Long itemId,
            MatchingRequestGroupItemStatus itemStatus,
            MatchingStatus matchingStatus,
            DevMatchingPersonResponse consumer
    ) {
        return new DevMatchingActionContext.RequestState(
                requestId,
                requestStatus,
                itemId,
                itemStatus,
                matchingStatus,
                consumer
        );
    }

    private DevMatchingPersonResponse consumer(Long memberId, String personaKey) {
        return new DevMatchingPersonResponse(
                DevMatchingPersonRole.CONSUMER,
                memberId,
                null,
                personaKey,
                "강습생 " + memberId
        );
    }

    private DevMatchingPersonResponse instructor() {
        return new DevMatchingPersonResponse(
                DevMatchingPersonRole.INSTRUCTOR,
                45L,
                5L,
                "instructor-b",
                "강사 B"
        );
    }

    private List<Long> memberIds(List<DevMatchingPersonResponse> people) {
        return people.stream().map(DevMatchingPersonResponse::memberId).toList();
    }

    private List<String> resourceKeys(DevMatchingActionPreviewResponse action) {
        return action.affectedResources().stream()
                .map(resource -> resource.resourceType() + "#" + resource.resourceId())
                .toList();
    }

    private List<String> resourceChangeKeys(DevMatchingActionPreviewResponse.Outcome outcome) {
        return outcome.resourceStateChanges().stream()
                .map(change -> change.resource().resourceType()
                        + "#" + change.resource().resourceId()
                        + "." + change.field()
                        + ":" + change.before()
                        + "->" + change.after())
                .toList();
    }

    private List<String> personChangeKeys(DevMatchingActionPreviewResponse.Outcome outcome) {
        return outcome.personStatusChanges().stream()
                .map(change -> change.person().memberId()
                        + ":" + change.before()
                        + "->" + change.after())
                .toList();
    }
}
