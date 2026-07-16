-- FLOW preparation only. No instructor exposure is added, and the request is created through REST.
START TRANSACTION;
SET @seed_now = UTC_TIMESTAMP(6);

SET @instructor_profile_id = (
    SELECT profile.id
    FROM instructor_profiles profile
    JOIN dev_personas persona ON persona.member_id = profile.member_id
    WHERE persona.persona_key = '보법다른-유정-승인강사'
);

INSERT INTO instructor_price_policies (
    instructor_profile_id,
    base_price_amount,
    additional_person_price_amount,
    is_active,
    created_at,
    updated_at
) VALUES (
    @instructor_profile_id,
    60000,
    20000,
    b'1',
    @seed_now,
    @seed_now
);

COMMIT;
