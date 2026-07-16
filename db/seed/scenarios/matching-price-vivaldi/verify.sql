-- A failed CHECK insert stops the reset/CI flow instead of hiding partial seed state.
CREATE TEMPORARY TABLE seed_contract_assertion (
    ok TINYINT NOT NULL,
    CONSTRAINT chk_seed_contract_assertion CHECK (ok = 1)
);

INSERT INTO seed_contract_assertion (ok)
SELECT (
    (SELECT COUNT(*)
     FROM platform_fee_policies
     WHERE is_active = b'1') = 1
    AND
    (SELECT COUNT(*)
     FROM platform_fee_policies
     WHERE is_active = b'1' AND fee_rate_bps = 0) = 1
    AND
    (SELECT COUNT(*)
     FROM resorts
     WHERE code = 'VIVALDI_PARK'
       AND display_name = '비발디파크'
       AND pass_fee_amount = 25000) = 1
    AND
    (SELECT COUNT(*)
     FROM dev_personas persona
     JOIN members member ON member.id = persona.member_id
     WHERE persona.persona_key IN ('consumer-default', 'instructor-approved-default')
       AND member.status = 'ACTIVE') = 2
    AND
    (SELECT COUNT(*)
     FROM instructor_profiles profile
     JOIN dev_personas persona ON persona.member_id = profile.member_id
     JOIN resorts resort ON resort.id = profile.resort_id
     WHERE persona.persona_key = 'instructor-approved-default'
       AND persona.template = 'INSTRUCTOR_APPROVED'
       AND profile.approval_status = 'APPROVED'
       AND profile.approved_at IS NOT NULL
       AND resort.code = 'VIVALDI_PARK') = 1
    AND
    (SELECT COUNT(*)
     FROM instructor_profile_certificates certificate
     JOIN instructor_profiles profile ON profile.id = certificate.instructor_profile_id
     JOIN dev_personas persona ON persona.member_id = profile.member_id
     WHERE persona.persona_key = 'instructor-approved-default'
       AND certificate.certificate_type = 'KSIA_SKI_LEVEL_1') = 1
    AND
    (SELECT COUNT(*)
     FROM instructor_matching_settings setting
     JOIN instructor_profiles profile ON profile.id = setting.instructor_profile_id
     JOIN dev_personas persona ON persona.member_id = profile.member_id
     WHERE persona.persona_key = 'instructor-approved-default'
       AND setting.sport = 'SKI'
       AND setting.max_headcount = 3
       AND setting.is_equipment_ready = b'1'
       AND setting.is_exposed = b'1') = 1
    AND
    (SELECT COUNT(*)
     FROM instructor_matching_settings_lesson_levels level
     JOIN instructor_matching_settings setting
       ON setting.id = level.instructor_matching_setting_id
     JOIN instructor_profiles profile ON profile.id = setting.instructor_profile_id
     JOIN dev_personas persona ON persona.member_id = profile.member_id
     WHERE persona.persona_key = 'instructor-approved-default'
       AND level.lesson_level IN ('FIRST_TIME', 'BEGINNER')) = 2
    AND
    (SELECT COUNT(*)
     FROM instructor_matching_settings_available_durations duration
     JOIN instructor_matching_settings setting
       ON setting.id = duration.instructor_matching_setting_id
     JOIN instructor_profiles profile ON profile.id = setting.instructor_profile_id
     JOIN dev_personas persona ON persona.member_id = profile.member_id
     WHERE persona.persona_key = 'instructor-approved-default'
       AND duration.available_duration_minutes = 120) = 1
    AND
    (SELECT COUNT(*)
     FROM instructor_price_policies policy
     JOIN instructor_profiles profile ON profile.id = policy.instructor_profile_id
     JOIN dev_personas persona ON persona.member_id = profile.member_id
     WHERE persona.persona_key = 'instructor-approved-default'
       AND policy.is_active = b'1'
       AND policy.base_price_amount = 60000
       AND policy.additional_person_price_amount = 20000) = 1
    AND
    (SELECT COUNT(*)
     FROM (
         SELECT profile.id
         FROM instructor_profiles profile
         LEFT JOIN instructor_price_policies policy
           ON policy.instructor_profile_id = profile.id
          AND policy.is_active = b'1'
         GROUP BY profile.id
         HAVING COUNT(policy.id) <> 1
     ) invalid_active_price_policy) = 0
    AND
    (SELECT COUNT(*)
     FROM matching_requests
     WHERE status IN ('REQUESTED', 'GROUPED', 'MATCHED')) = 0
);

DROP TEMPORARY TABLE seed_contract_assertion;

SELECT
    persona.persona_key,
    persona.member_id
FROM dev_personas persona
ORDER BY persona.persona_key;
