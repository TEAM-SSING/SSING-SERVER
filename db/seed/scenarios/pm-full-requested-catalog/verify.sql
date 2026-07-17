-- A failed CHECK insert stops reset/CI and preserves the exact broken invariant.
CREATE TEMPORARY TABLE seed_contract_assertion (
    ok TINYINT NOT NULL,
    CONSTRAINT chk_seed_contract_assertion CHECK (ok = 1)
);

INSERT INTO seed_contract_assertion (ok)
SELECT (
    (SELECT COUNT(*) FROM platform_fee_policies WHERE is_active = b'1' AND fee_rate_bps = 0) = 1
    AND
    (SELECT COUNT(*) FROM resorts) = 11
    AND
    (SELECT SUM(pass_fee_amount) FROM resorts) = 305000
    AND
    (SELECT COUNT(*)
     FROM resorts
     WHERE code = 'O2_RESORT'
       AND name = '오투리조트'
       AND display_name = '오투리조트'
       AND pass_fee_amount = 30000) = 1
    AND
    (SELECT COUNT(*)
     FROM resorts
     WHERE code = 'MUJU_DEOGYUSAN_RESORT'
       AND name = '무주덕유산리조트'
       AND display_name = '무주덕유산리조트'
       AND pass_fee_amount = 30000) = 1
    AND
    (SELECT COUNT(*) FROM dev_personas) = 14
    AND
    (SELECT COUNT(*)
     FROM dev_personas persona
     JOIN members member ON member.id = persona.member_id
     WHERE member.role = 'CONSUMER'
       AND (persona.persona_key, member.nickname) IN (
           ('폭룡적-예지-일반강습생', '폭룡적 예지'),
           ('느좋그자체-예림-일반강습생', '느좋 그 자체 예림'),
           ('감다살-유빈-일반강습생', '감다살 유빈'),
           ('야르-선문-일반강습생', '야르 선문'),
           ('난리자베스-채원-일반강습생', '난리자베스 채원'),
           ('대뜸GOAT-성빈-일반강습생', '대뜸 GOAT 성빈'),
           ('도파민풀충-나현-일반강습생', '도파민 풀충 나현'),
           ('레전드갱신중인-지환-일반강습생', '레전드 갱신 중인 지환'),
           ('갑차기스러운-예슬-일반강습생', '갑차기스러운 예슬')
       )) = 9
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
    (SELECT COUNT(*) FROM instructor_profiles) = 4
    AND
    (SELECT COUNT(*)
     FROM instructor_profiles
     WHERE phone LIKE '010-0000-%'
       AND approval_status = 'APPROVED'
       AND approved_at IS NOT NULL
       AND level = 1
       AND experience = 0) = 4
    AND
    (SELECT COUNT(*)
     FROM instructor_profiles profile
     JOIN dev_personas persona ON persona.member_id = profile.member_id
     JOIN members member ON member.id = profile.member_id
     WHERE profile.real_name = member.nickname
       AND (persona.persona_key, member.nickname) IN (
           ('기세로다해먹는-도연-승인강사', '기세로 다 해먹는 도연'),
           ('폼미친-성빈-승인강사', '폼 미친 성빈'),
           ('뉴런공유중인-유정-승인강사', '뉴런 공유 중인 유정'),
           ('보법다른-유정-승인강사', '보법 다른 유정')
       )
       AND (
           (persona.persona_key = '보법다른-유정-승인강사'
            AND profile.phone = '010-0000-0000'
            AND profile.birth_date = '2000-01-04')
           OR
           (persona.persona_key <> '보법다른-유정-승인강사'
            AND profile.phone REGEXP '^010-0000-[0-9]{4}$'
            AND profile.birth_date IN ('2000-01-01', '2000-01-02', '2000-01-03'))
       )) = 4
    AND
    (SELECT COUNT(*) FROM instructor_profile_certificates) = 4
    AND
    (SELECT COUNT(*)
     FROM instructor_profile_certificates
     WHERE certificate_type = 'KSIA_SKI_LEVEL_1') = 3
    AND
    (SELECT COUNT(*)
     FROM instructor_profile_certificates
     WHERE certificate_type = 'KSIA_SKI_LEVEL_2') = 1
    AND
    (SELECT COUNT(*) FROM instructor_matching_settings WHERE is_exposed = b'1') = 4
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
    (SELECT COUNT(*) FROM matching_requests) = 16
    AND
    (SELECT COUNT(*) FROM matching_requests WHERE status = 'REQUESTED' AND status_reason IS NULL) = 9
    AND
    (SELECT COUNT(*)
     FROM matching_requests
     WHERE status = 'CANCELED'
       AND status_reason = 'CONSUMER_CANCELED'
       AND canceled_at IS NOT NULL) = 7
    AND
    (SELECT COUNT(*)
     FROM (
         SELECT member_id
         FROM matching_requests
         WHERE status IN ('REQUESTED', 'GROUPED', 'MATCHED')
         GROUP BY member_id
         HAVING COUNT(*) > 1
     ) duplicate_active_request) = 0
    AND
    (SELECT COUNT(*) FROM matching_requests WHERE expires_at IS NOT NULL OR matching_offer_id IS NOT NULL) = 0
    AND
    (SELECT COUNT(*) FROM matching_requests_requested_duration_minutes) = 16
    AND
    (SELECT COUNT(*)
     FROM (
         SELECT matching_request_id
         FROM matching_requests_requested_duration_minutes
         GROUP BY matching_request_id
         HAVING COUNT(*) = 1
            AND MIN(duration_minutes) IN (120, 180, 240)
     ) request_duration_contract) = 16
    AND
    (SELECT COUNT(*) FROM matching_request_participants) = 49
    AND
    (SELECT COUNT(*)
     FROM matching_requests request
     JOIN (
         SELECT matching_request_id, COUNT(*) AS participant_count
         FROM matching_request_participants
         GROUP BY matching_request_id
     ) participant ON participant.matching_request_id = request.id
     WHERE participant.participant_count = request.headcount) = 16
    AND
    (SELECT COUNT(*)
     FROM matching_requests request
     JOIN dev_personas persona ON persona.member_id = request.member_id
     WHERE persona.persona_key = '도파민풀충-나현-일반강습생') = 4
    AND
    (SELECT COUNT(*)
     FROM matching_requests request
     JOIN dev_personas persona ON persona.member_id = request.member_id
     WHERE persona.persona_key = '레전드갱신중인-지환-일반강습생') = 4
    AND
    (SELECT COUNT(*)
     FROM matching_requests request
     JOIN dev_personas persona ON persona.member_id = request.member_id
     WHERE persona.persona_key = '갑차기스러운-예슬-일반강습생') = 2
    AND
    (SELECT COUNT(*) FROM matching_request_groups) = 0
    AND
    (SELECT COUNT(*) FROM matching_offers) = 0
    AND
    (SELECT COUNT(*) FROM matching_request_payments) = 0
    AND
    (SELECT COUNT(*) FROM lessons) = 0
);

DROP TEMPORARY TABLE seed_contract_assertion;

SELECT
    persona.persona_key,
    persona.member_id
FROM dev_personas persona
ORDER BY persona.persona_key;
