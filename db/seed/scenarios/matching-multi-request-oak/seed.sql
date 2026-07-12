START TRANSACTION;

SET @seed_now = UTC_TIMESTAMP(6);
SET @scenario_member_id = CAST(UNIX_TIMESTAMP(@seed_now) * 1000000 AS UNSIGNED) + 100;

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
    '다중요청소비자',
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
    'pm-consumer-007',
    @scenario_member_id,
    'GENERAL_CONSUMER',
    @seed_now,
    @seed_now
);

COMMIT;
