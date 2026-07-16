START TRANSACTION;

SET @seed_now = UTC_TIMESTAMP(6);
SET @scenario_member_id = CAST(UNIX_TIMESTAMP(@seed_now) * 1000000 AS UNSIGNED) + 100;
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

INSERT INTO members (
    id,
    nickname,
    profile_image_url,
    role,
    status,
    created_at,
    updated_at
) VALUES (
    @scenario_member_id,
    '도파민 풀충 나현',
    NULL,
    'CONSUMER',
    'ACTIVE',
    @seed_now,
    @seed_now
);

INSERT INTO dev_personas (
    persona_key,
    member_id,
    template,
    created_at,
    updated_at
) VALUES (
    '도파민풀충-나현-일반강습생',
    @scenario_member_id,
    'GENERAL_CONSUMER',
    @seed_now,
    @seed_now
);

COMMIT;
