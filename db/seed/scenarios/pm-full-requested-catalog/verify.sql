-- A failed CHECK insert stops reset/CI and preserves the exact broken invariant.
CREATE TEMPORARY TABLE seed_contract_assertion (
    ok TINYINT NOT NULL,
    CONSTRAINT chk_seed_contract_assertion CHECK (ok = 1)
);

INSERT INTO seed_contract_assertion (ok)
SELECT (
    (SELECT COUNT(*) FROM platform_fee_policies WHERE is_active = b'1' AND fee_rate_bps = 0) = 1
    AND
    (SELECT COUNT(*) FROM resorts) = 9
    AND
    (SELECT SUM(pass_fee_amount) FROM resorts) = 245000
    AND
    (SELECT COUNT(*) FROM dev_personas) = 13
    AND
    (SELECT COUNT(*)
     FROM dev_personas persona
     JOIN members member ON member.id = persona.member_id
     WHERE member.role = 'CONSUMER'
       AND (
           (persona.persona_key LIKE 'pm-consumer-%'
            AND member.nickname REGEXP '^목데이터소비자[0-9]{2}$')
           OR
           (persona.persona_key = 'consumer-default'
            AND member.nickname = '가격검증소비자')
       )) = 9
    AND
    (SELECT COUNT(*)
     FROM dev_personas persona
     JOIN members member ON member.id = persona.member_id
     WHERE member.status = 'ACTIVE'
       AND (
           (member.role = 'CONSUMER' AND persona.template = 'GENERAL_CONSUMER')
           OR (member.role = 'INSTRUCTOR' AND persona.template = 'INSTRUCTOR_APPROVED')
       )) = 13
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
     WHERE (
         (persona.persona_key LIKE 'pm-instructor-approved-%'
          AND member.nickname REGEXP '^목데이터강사[0-9]{2}$'
          AND profile.real_name = member.nickname
          AND profile.phone REGEXP '^010-0000-[0-9]{4}$'
          AND profile.birth_date IN ('2000-01-01', '2000-01-02', '2000-01-03'))
         OR
         (persona.persona_key = 'instructor-approved-default'
          AND member.nickname = '가격검증강사'
          AND profile.real_name = '가격검증강사'
          AND profile.phone = '010-0000-0000'
          AND profile.birth_date = '1994-02-24')
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
    (SELECT COUNT(*) FROM matching_requests) = 16
    AND
    (SELECT COUNT(*) FROM matching_requests WHERE status = 'REQUESTED' AND status_reason IS NULL) = 16
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
     WHERE persona.persona_key = 'pm-consumer-007') = 4
    AND
    (SELECT COUNT(*)
     FROM matching_requests request
     JOIN dev_personas persona ON persona.member_id = request.member_id
     WHERE persona.persona_key = 'pm-consumer-008') = 4
    AND
    (SELECT COUNT(*)
     FROM matching_requests request
     JOIN dev_personas persona ON persona.member_id = request.member_id
     WHERE persona.persona_key = 'pm-consumer-009') = 2
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
