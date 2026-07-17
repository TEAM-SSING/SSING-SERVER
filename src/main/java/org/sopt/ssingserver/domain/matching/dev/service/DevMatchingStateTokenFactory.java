package org.sopt.ssingserver.domain.matching.dev.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import org.sopt.ssingserver.domain.matching.dev.dto.response.DevMatchingActionPreviewResponse;
import org.sopt.ssingserver.domain.matching.dev.dto.response.DevMatchingParticipantResponse;
import org.sopt.ssingserver.domain.matching.dev.dto.response.DevMatchingPersonResponse;
import org.sopt.ssingserver.domain.matching.dev.dto.response.DevMatchingRequestRelationResponse;
import org.sopt.ssingserver.domain.matching.dev.dto.response.DevMatchingResourceResponse;
import org.sopt.ssingserver.domain.matching.enums.MatchingStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile({"local", "dev"})
@Component
class DevMatchingStateTokenFactory {

    private static final String VERSION = "v1";

    // #159가 조회 이후 변경 여부를 비교할 수 있도록 관련 사람·row·action을 정렬한 fingerprint를 만든다.
    String create(
            Long matchingRequestId,
            MatchingStatus matchingStatus,
            List<DevMatchingPersonResponse> people,
            List<DevMatchingRequestRelationResponse> requestRelations,
            List<DevMatchingParticipantResponse> participants,
            List<DevMatchingResourceResponse> resources,
            List<DevMatchingActionPreviewResponse> actions
    ) {
        List<String> parts = new ArrayList<>();
        parts.add("request|" + matchingRequestId);
        parts.add("matchingStatus|" + value(matchingStatus));
        people.stream()
                .map(person -> "person|" + person.personRole()
                        + "|" + value(person.memberId())
                        + "|" + value(person.instructorProfileId())
                        + "|" + value(person.personaKey())
                        + "|" + value(person.displayName()))
                .forEach(parts::add);
        requestRelations.stream()
                .map(relation -> "requestRelation|" + relation.matchingRequestId()
                        + "|" + relation.consumerMemberId()
                        + "|" + value(relation.groupItemId())
                        + "|" + value(relation.groupId())
                        + "|" + value(relation.offerId())
                        + "|" + value(relation.paymentId())
                        + "|" + value(relation.matchingStatus()))
                .forEach(parts::add);
        participants.stream()
                .map(participant -> "participant|" + participant.participantId()
                        + "|" + participant.matchingRequestId()
                        + "|" + value(participant.name())
                        + "|" + participant.age()
                        + "|" + participant.gender())
                .forEach(parts::add);
        resources.stream()
                .map(resource -> "resource|" + resource.resourceType()
                        + "|" + value(resource.resourceId())
                        + "|" + value(resource.status())
                        + "|" + value(resource.statusReason())
                        + "|" + value(resource.createdAt())
                        + "|" + value(resource.updatedAt()))
                .forEach(parts::add);
        actions.stream()
                .map(action -> "action|" + action.actionKey())
                .forEach(parts::add);
        parts.sort(Comparator.naturalOrder());

        MessageDigest digest = sha256();
        byte[] hash = digest.digest(String.join("\n", parts).getBytes(StandardCharsets.UTF_8));
        return VERSION + ":" + HexFormat.of().formatHex(hash);
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    private String value(Object value) {
        return value == null ? "<null>" : value.toString();
    }
}
