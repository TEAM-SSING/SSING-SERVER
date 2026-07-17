-- 공유 dev DB는 강사 기반 정보만 유지하고 매칭 흐름은 비어 있어야 한다.
CREATE TEMPORARY TABLE dev_reset_contract_assertion (
    ok TINYINT NOT NULL,
    CONSTRAINT chk_dev_reset_contract_assertion CHECK (ok = 1)
);

INSERT INTO dev_reset_contract_assertion (ok)
SELECT (
    (SELECT COUNT(*)
     FROM dev_personas persona
     JOIN members member ON member.id = persona.member_id
     WHERE persona.template = 'INSTRUCTOR_APPROVED'
       AND member.role = 'INSTRUCTOR'
       AND member.status = 'ACTIVE') > 0
    AND
    (SELECT COUNT(*)
     FROM instructor_profiles
     WHERE approval_status = 'APPROVED'
       AND approved_at IS NOT NULL) = (SELECT COUNT(*) FROM instructor_profiles)
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
     FROM (
         SELECT persona.member_id
         FROM dev_personas persona
         JOIN members member ON member.id = persona.member_id
         LEFT JOIN instructor_profiles profile ON profile.member_id = persona.member_id
         LEFT JOIN instructor_price_policies policy
           ON policy.instructor_profile_id = profile.id
          AND policy.is_active = b'1'
         WHERE persona.template = 'INSTRUCTOR_APPROVED'
           AND member.role = 'INSTRUCTOR'
           AND member.status = 'ACTIVE'
         GROUP BY persona.member_id
         HAVING COUNT(DISTINCT CASE
                    WHEN profile.approval_status = 'APPROVED'
                     AND profile.approved_at IS NOT NULL
                    THEN profile.id
                END) <> 1
             OR COUNT(DISTINCT policy.id) <> 1
     ) incomplete_instructor_persona) = 0
    AND
    (SELECT COUNT(*)
     FROM instructor_matching_settings
     WHERE is_exposed = b'1') = 0
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

DROP TEMPORARY TABLE dev_reset_contract_assertion;
