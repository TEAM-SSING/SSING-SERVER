package org.sopt.ssingserver.domain.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;
import org.sopt.ssingserver.global.entity.BaseTimeEntity;

@Getter
@Entity
@Table(name = "members")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(length = 500)
    private String profileImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MemberStatus status;

    public static Member createOAuthMember(String nickname, String profileImageUrl) {
        // 강사 앱으로 가입해도 승인 전까지는 소비자 권한으로 시작
        return create(
                nickname,
                profileImageUrl,
                MemberRole.CONSUMER,
                MemberStatus.ACTIVE
        );
    }

    public static Member create(
            String nickname,
            String profileImageUrl,
            MemberRole role,
            MemberStatus status
    ) {
        Member member = new Member();
        member.nickname = nickname;
        member.profileImageUrl = profileImageUrl;
        member.role = role;
        member.status = status;
        return member;
    }

    public void promoteToInstructor() {
        if (status != MemberStatus.ACTIVE || role != MemberRole.CONSUMER) {
            throw new IllegalStateException("Only active consumers can be promoted to instructor.");
        }
        role = MemberRole.INSTRUCTOR;
    }
}
