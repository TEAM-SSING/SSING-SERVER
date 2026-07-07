package org.sopt.ssingserver.domain.instructor.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.sopt.ssingserver.domain.instructor.enums.InstructorApprovalStatus;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.member.enums.Gender;
import org.sopt.ssingserver.domain.resort.entity.Resort;
import org.sopt.ssingserver.global.entity.BaseTimeEntity;

@Getter
@Entity
@Table(
        name = "instructor_profiles",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_instructor_profiles_member",
                columnNames = "member_id"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InstructorProfile extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn
    private Resort resort;

    @Column(nullable = false, length = 50)
    private String realName;

    @Column(nullable = false, length = 30)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Gender gender;

    @Column(nullable = false)
    private LocalDate birthDate;

    @Column(columnDefinition = "TEXT")
    private String intro;

    @Column(nullable = false)
    private LocalDate careerStartDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private InstructorApprovalStatus approvalStatus;

    private Instant approvedAt;

    public static InstructorProfile create(
            Member member,
            String realName,
            String phone,
            Gender gender,
            LocalDate birthDate,
            String intro,
            LocalDate careerStartDate,
            InstructorApprovalStatus approvalStatus,
            Instant approvedAt
    ) {
        InstructorProfile instructorProfile = new InstructorProfile();
        instructorProfile.member = member;
        instructorProfile.realName = realName;
        instructorProfile.phone = phone;
        instructorProfile.gender = gender;
        instructorProfile.birthDate = birthDate;
        instructorProfile.intro = intro;
        instructorProfile.careerStartDate = careerStartDate;
        instructorProfile.approvalStatus = approvalStatus;
        instructorProfile.approvedAt = approvedAt;
        return instructorProfile;
    }
}
