-- Base means every QA persona is ready to log in, while nobody has entered matching yet.
CREATE TEMPORARY TABLE base_seed_contract_assertion (
    ok TINYINT NOT NULL,
    CONSTRAINT chk_base_seed_contract_assertion CHECK (ok = 1)
);

INSERT INTO base_seed_contract_assertion (ok)
SELECT (
    (SELECT COUNT(*)
     FROM platform_fee_policies
     WHERE fee_rate_bps = 0
       AND is_active = b'1') = 1
    AND
    (SELECT COUNT(*)
     FROM resorts
     WHERE code IN (
         'HIGH1',
         'PHOENIX_PARK',
         'VIVALDI_PARK',
         'WELLI_HILLI_PARK',
         'ELYSIAN_GANGCHON',
         'OAK_VALLEY',
         'ALPENSIA',
         'O2_RESORT',
         'KONJIAM_RESORT',
         'JISAN_FOREST_RESORT',
         'MUJU_DEOGYUSAN_RESORT'
     )) = 11
    AND
    (SELECT COUNT(*) FROM dev_personas) = 14
    AND
    (SELECT COUNT(*)
     FROM dev_personas
     WHERE persona_key IN (
         '대뜸GOAT-성빈-일반강습생',
         '보법다른-유정-승인강사',
         '폭룡적-예지-일반강습생',
         '느좋그자체-예림-일반강습생',
         '감다살-유빈-일반강습생',
         '야르-선문-일반강습생',
         '난리자베스-채원-일반강습생',
         '도파민풀충-나현-일반강습생',
         '레전드갱신중인-지환-일반강습생',
         '갑차기스러운-예슬-일반강습생',
         '기세로다해먹는-도연-승인강사',
         '폼미친-성빈-승인강사',
         '뉴런공유중인-유정-승인강사',
         '냅다레전드-유빈-일반강습생'
     )) = 14
    AND
    (SELECT COUNT(*)
     FROM dev_personas persona
     JOIN members member ON member.id = persona.member_id
     WHERE member.status = 'ACTIVE'
       AND (
           (member.role = 'CONSUMER' AND persona.template = 'GENERAL_CONSUMER')
           OR (member.role = 'INSTRUCTOR' AND persona.template = 'INSTRUCTOR_APPROVED')
       )) = 14
    AND
    (SELECT COUNT(*) FROM instructor_profiles WHERE approval_status = 'APPROVED') = 4
    AND
    (SELECT COUNT(*)
     FROM instructor_profiles profile
     JOIN dev_personas persona ON persona.member_id = profile.member_id
     WHERE (persona.persona_key = '폼미친-성빈-승인강사' AND profile.gender = 'MALE')
        OR (persona.persona_key IN (
                '기세로다해먹는-도연-승인강사',
                '뉴런공유중인-유정-승인강사',
                '보법다른-유정-승인강사'
            ) AND profile.gender = 'FEMALE')) = 4
    AND
    (SELECT COUNT(*)
     FROM instructor_profiles
     WHERE CHAR_LENGTH(intro) >= 20
       AND intro NOT LIKE '%fixture%'
       AND intro NOT LIKE '%검증%'
       AND intro NOT LIKE '%로컬 전용%') = 4
    AND
    (SELECT COUNT(*) FROM instructor_profile_certificates) = 4
    AND
    (SELECT COUNT(*) FROM instructor_matching_settings) = 4
    AND
    (SELECT COUNT(*) FROM instructor_matching_settings WHERE is_exposed = b'1') = 0
    AND
    (SELECT COUNT(*) FROM instructor_matching_settings_lesson_levels) = 9
    AND
    (SELECT COUNT(*) FROM instructor_matching_settings_available_durations) = 4
    AND
    (SELECT COUNT(*) FROM instructor_price_policies WHERE is_active = b'1') = 4
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
    (
        (SELECT COUNT(*) FROM matching_requests)
        + (SELECT COUNT(*) FROM matching_requests_requested_duration_minutes)
        + (SELECT COUNT(*) FROM matching_request_participants)
        + (SELECT COUNT(*) FROM matching_request_groups)
        + (SELECT COUNT(*) FROM matching_request_group_items)
        + (SELECT COUNT(*) FROM matching_offers)
        + (SELECT COUNT(*) FROM matching_offer_price_snapshots)
        + (SELECT COUNT(*) FROM matching_request_price_snapshots)
        + (SELECT COUNT(*) FROM matching_request_payments)
        + (SELECT COUNT(*) FROM lessons)
        + (SELECT COUNT(*) FROM lesson_participants)
        + (SELECT COUNT(*) FROM lesson_cancellations)
        + (SELECT COUNT(*) FROM lesson_start_confirmations)
    ) = 0
);

DROP TEMPORARY TABLE base_seed_contract_assertion;
