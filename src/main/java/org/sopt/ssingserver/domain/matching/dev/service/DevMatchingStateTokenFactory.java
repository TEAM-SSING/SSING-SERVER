package org.sopt.ssingserver.domain.matching.dev.service;

import java.nio.ByteBuffer;
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

    private static final String VERSION = "v2";

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
        parts.add(part("request", matchingRequestId));
        parts.add(part("matchingStatus", matchingStatus));
        people.stream()
                .map(person -> part(
                        "person",
                        person.personRole(),
                        person.memberId(),
                        person.instructorProfileId(),
                        person.personaKey(),
                        person.displayName()
                ))
                .forEach(parts::add);
        requestRelations.stream()
                .map(relation -> part(
                        "requestRelation",
                        relation.matchingRequestId(),
                        relation.consumerMemberId(),
                        relation.groupItemId(),
                        relation.groupId(),
                        relation.offerId(),
                        relation.paymentId(),
                        relation.matchingStatus()
                ))
                .forEach(parts::add);
        participants.stream()
                .map(participant -> part(
                        "participant",
                        participant.participantId(),
                        participant.matchingRequestId(),
                        participant.name(),
                        participant.age(),
                        participant.gender()
                ))
                .forEach(parts::add);
        resources.stream()
                .map(resource -> part(
                        "resource",
                        resource.resourceType(),
                        resource.resourceId(),
                        resource.status(),
                        resource.statusReason(),
                        resource.createdAt(),
                        resource.updatedAt()
                ))
                .forEach(parts::add);
        actions.stream()
                .map(action -> part("action", action.actionKey()))
                .forEach(parts::add);
        parts.sort(Comparator.naturalOrder());

        MessageDigest digest = sha256();
        digest.update(intBytes(parts.size()));
        parts.forEach(value -> update(digest, value));
        byte[] hash = digest.digest();
        return VERSION + ":" + HexFormat.of().formatHex(hash);
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    private String part(String type, Object... values) {
        StringBuilder result = new StringBuilder();
        appendField(result, type);
        for (Object value : values) {
            appendField(result, value);
        }
        return result.toString();
    }

    private void appendField(StringBuilder result, Object value) {
        // 길이와 null tag를 함께 기록해 값 안의 구분자와 필드 경계가 섞이지 않게 한다.
        if (value == null) {
            result.append("N;");
            return;
        }

        String text = value.toString();
        int byteLength = text.getBytes(StandardCharsets.UTF_8).length;
        result.append('S').append(byteLength).append(':').append(text).append(';');
    }

    private void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(intBytes(bytes.length));
        digest.update(bytes);
    }

    private byte[] intBytes(int value) {
        return ByteBuffer.allocate(Integer.BYTES).putInt(value).array();
    }
}
