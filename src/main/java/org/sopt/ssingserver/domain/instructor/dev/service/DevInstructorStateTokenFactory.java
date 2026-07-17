package org.sopt.ssingserver.domain.instructor.dev.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import org.sopt.ssingserver.domain.auth.entity.OAuthAccount;
import org.sopt.ssingserver.domain.instructor.dev.enums.DevInstructorActionKey;
import org.sopt.ssingserver.domain.instructor.entity.InstructorMatchingSetting;
import org.sopt.ssingserver.domain.instructor.entity.InstructorPricePolicy;
import org.sopt.ssingserver.domain.instructor.entity.InstructorProfile;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile({"local", "dev"})
@ConditionalOnProperty(name = "ssing.dev-instructor-actions.enabled", havingValue = "true")
@Component
class DevInstructorStateTokenFactory {

    private static final String VERSION = "v1";

    String create(
            OAuthAccount oauthAccount,
            Member member,
            InstructorProfile profile,
            InstructorMatchingSetting setting,
            List<InstructorPricePolicy> activePricePolicies,
            List<DevInstructorActionKey> actions
    ) {
        List<String> parts = new ArrayList<>();
        parts.add("oauth|" + value(oauthAccount.getId())
                + "|" + oauthAccount.getProvider()
                + "|" + value(oauthAccount.getUpdatedAt()));
        parts.add("member|" + value(member.getId())
                + "|" + value(member.getNickname())
                + "|" + member.getRole()
                + "|" + member.getStatus()
                + "|" + value(member.getUpdatedAt()));
        if (profile == null) {
            parts.add("profile|<absent>");
        } else {
            parts.add("profile|" + value(profile.getId())
                    + "|" + profile.getApprovalStatus()
                    + "|" + value(profile.getApprovedAt())
                    + "|" + value(profile.getResort() == null ? null : profile.getResort().getId())
                    + "|" + value(profile.getUpdatedAt()));
            profile.getCertificateTypes().stream()
                    .map(certificate -> "certificate|" + certificate)
                    .forEach(parts::add);
        }
        if (setting == null) {
            parts.add("setting|<absent>");
        } else {
            parts.add("setting|" + value(setting.getId())
                    + "|" + setting.getSport()
                    + "|" + setting.getMaxHeadcount()
                    + "|" + setting.isEquipmentReady()
                    + "|" + setting.isExposed()
                    + "|" + value(setting.getUpdatedAt()));
            setting.getLessonLevels().stream()
                    .map(level -> "lessonLevel|" + level)
                    .forEach(parts::add);
            setting.getAvailableDurationMinutes().stream()
                    .map(duration -> "duration|" + duration)
                    .forEach(parts::add);
        }
        activePricePolicies.stream()
                .map(policy -> "price|" + value(policy.getId())
                        + "|" + policy.getBasePriceAmount()
                        + "|" + policy.getAdditionalPersonPriceAmount()
                        + "|" + policy.isActive()
                        + "|" + value(policy.getUpdatedAt()))
                .forEach(parts::add);
        actions.stream().map(action -> "action|" + action).forEach(parts::add);
        parts.sort(Comparator.naturalOrder());

        byte[] hash = sha256().digest(String.join("\n", parts).getBytes(StandardCharsets.UTF_8));
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
