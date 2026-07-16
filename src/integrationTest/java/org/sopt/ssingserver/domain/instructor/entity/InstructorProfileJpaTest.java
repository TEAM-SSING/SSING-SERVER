package org.sopt.ssingserver.domain.instructor.entity;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.sopt.ssingserver.database.support.DatabaseCleaner;
import org.sopt.ssingserver.database.support.SharedMySqlDatabase;
import org.sopt.ssingserver.domain.instructor.enums.InstructorApprovalStatus;
import org.sopt.ssingserver.domain.instructor.enums.InstructorCertificateType;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.domain.instructor.repository.InstructorProfileRepository;
import org.sopt.ssingserver.domain.member.entity.Member;
import org.sopt.ssingserver.domain.member.enums.Gender;
import org.sopt.ssingserver.domain.member.enums.MemberRole;
import org.sopt.ssingserver.domain.member.enums.MemberStatus;
import org.sopt.ssingserver.domain.resort.entity.Resort;
import org.sopt.ssingserver.global.config.JpaAuditingConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class InstructorProfileJpaTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private InstructorProfileRepository instructorProfileRepository;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        SharedMySqlDatabase.configureDatasource(registry);
    }

    @BeforeTransaction
    void cleanDatabaseBeforeTransaction() {
        DatabaseCleaner.clean(dataSource);
    }

    @Test
    void 기존_단일_자격증과_새_다중_자격증을_MySQL에_저장하고_함께_조회한다() {
        Member member = Member.create(
                "승인강사",
                null,
                MemberRole.INSTRUCTOR,
                MemberStatus.ACTIVE
        );
        entityManager.persist(member);

        Resort resort = resort();
        entityManager.persist(resort);

        InstructorProfile profile = InstructorProfile.create(
                member,
                "승인강사",
                "010-0000-0000",
                Gender.MALE,
                LocalDate.of(2000, 1, 1),
                "테스트 강사 프로필",
                LocalDate.of(2020, 1, 1),
                InstructorApprovalStatus.APPROVED,
                Instant.parse("2026-07-07T00:00:00Z")
        );
        ReflectionTestUtils.setField(profile, "resort", resort);
        ReflectionTestUtils.setField(
                profile,
                "certificateType",
                InstructorCertificateType.KSIA_SNOWBOARD_LEVEL_1
        );
        profile.registerCertificate(InstructorCertificateType.KSIA_SKI_LEVEL_1);
        profile.registerCertificate(InstructorCertificateType.SBAK_SNOWBOARD_TEACHING_2);

        entityManager.persist(profile);
        entityManager.flush();
        Long memberId = member.getId();
        entityManager.clear();

        InstructorProfile reloaded = instructorProfileRepository.findByMemberId(memberId).orElseThrow();

        assertThat(reloaded.getCertificateTypes()).containsExactlyInAnyOrder(
                InstructorCertificateType.KSIA_SNOWBOARD_LEVEL_1,
                InstructorCertificateType.KSIA_SKI_LEVEL_1,
                InstructorCertificateType.SBAK_SNOWBOARD_TEACHING_2
        );
        assertThat(reloaded.getAvailableSports()).containsExactly(Sport.SKI, Sport.SNOWBOARD);
    }

    private Resort resort() {
        Resort resort = newResort();
        ReflectionTestUtils.setField(resort, "code", "HIGH1");
        ReflectionTestUtils.setField(resort, "name", "하이원 리조트");
        ReflectionTestUtils.setField(resort, "displayName", "하이원");
        return resort;
    }

    private Resort newResort() {
        try {
            var constructor = Resort.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
