package org.sopt.ssingserver.domain.matching.dev.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.domain.matching.dev.dto.response.DevMatchingParticipantResponse;
import org.sopt.ssingserver.domain.matching.dev.dto.response.DevMatchingPersonResponse;
import org.sopt.ssingserver.domain.matching.dev.dto.response.DevMatchingRequestRelationResponse;
import org.sopt.ssingserver.domain.matching.dev.dto.response.DevMatchingResourceResponse;
import org.sopt.ssingserver.domain.matching.dev.enums.DevMatchingPersonRole;
import org.sopt.ssingserver.domain.matching.dev.enums.DevMatchingResourceType;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
import org.sopt.ssingserver.domain.member.enums.Gender;

class DevMatchingStateTokenFactoryTest {

    private final DevMatchingStateTokenFactory factory = new DevMatchingStateTokenFactory();

    @Test
    void stateToken은_입력순서에는_안정적이고_자식상태변경에는_민감하다() {
        DevMatchingPersonResponse consumer = new DevMatchingPersonResponse(
                DevMatchingPersonRole.CONSUMER,
                12L,
                null,
                "consumer-a",
                "강습생 A"
        );
        DevMatchingResourceResponse request = resource(
                DevMatchingResourceType.MATCHING_REQUEST,
                301L,
                "GROUPED"
        );
        DevMatchingResourceResponse offer = resource(
                DevMatchingResourceType.MATCHING_OFFER,
                77L,
                "OFFERED"
        );
        DevMatchingRequestRelationResponse relation = new DevMatchingRequestRelationResponse(
                301L,
                12L,
                302L,
                98L,
                77L,
                null,
                MatchingStatus.WAITING_FOR_INSTRUCTOR
        );

        String first = factory.create(
                301L,
                MatchingStatus.WAITING_FOR_INSTRUCTOR,
                List.of(consumer),
                List.of(relation),
                List.of(),
                List.of(request, offer),
                List.of()
        );
        String reordered = factory.create(
                301L,
                MatchingStatus.WAITING_FOR_INSTRUCTOR,
                List.of(consumer),
                List.of(relation),
                List.of(),
                List.of(offer, request),
                List.of()
        );
        String changed = factory.create(
                301L,
                MatchingStatus.WAITING_FOR_CONFIRMATION,
                List.of(consumer),
                List.of(new DevMatchingRequestRelationResponse(
                        301L,
                        12L,
                        302L,
                        98L,
                        77L,
                        401L,
                        MatchingStatus.WAITING_FOR_CONFIRMATION
                )),
                List.of(),
                List.of(request, resource(DevMatchingResourceType.MATCHING_OFFER, 77L, "ACCEPTED")),
                List.of()
        );

        assertThat(first).startsWith("v1:").isEqualTo(reordered).isNotEqualTo(changed);
    }

    @Test
    void stateToken은_participant_이름변경에도_민감하다() {
        String before = factory.create(
                301L,
                MatchingStatus.SEARCHING,
                List.of(),
                List.of(),
                List.of(participant("테스트 참가자")),
                List.of(),
                List.of()
        );
        String after = factory.create(
                301L,
                MatchingStatus.SEARCHING,
                List.of(),
                List.of(),
                List.of(participant("이름 변경 참가자")),
                List.of(),
                List.of()
        );

        assertThat(before).isNotEqualTo(after);
    }

    private DevMatchingParticipantResponse participant(String name) {
        return new DevMatchingParticipantResponse(501L, 301L, name, 24, Gender.FEMALE);
    }

    private DevMatchingResourceResponse resource(
            DevMatchingResourceType type,
            Long id,
            String status
    ) {
        return new DevMatchingResourceResponse(
                type,
                id,
                status,
                null,
                Instant.parse("2026-07-15T00:00:00Z"),
                Instant.parse("2026-07-15T00:01:00Z")
        );
    }
}
