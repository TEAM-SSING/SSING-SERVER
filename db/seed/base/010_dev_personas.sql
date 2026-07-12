-- Local/CI-only personas. Names and phone are anonymized seed values.
START TRANSACTION;

SET @seed_now = UTC_TIMESTAMP(6);
SET @seed_member_id_base = CAST(UNIX_TIMESTAMP(@seed_now) * 1000000 AS UNSIGNED);

INSERT INTO members (
    id,
    nickname,
    profile_image_url,
    role,
    status,
    created_at,
    updated_at
) VALUES
(
    @seed_member_id_base + 1,
    '가격검증소비자',
    NULL,
    'CONSUMER',
    'ACTIVE',
    @seed_now,
    @seed_now
),
(
    @seed_member_id_base + 2,
    '가격검증강사',
    NULL,
    'INSTRUCTOR',
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
) VALUES
(
    'consumer-default',
    @seed_member_id_base + 1,
    'GENERAL_CONSUMER',
    @seed_now,
    @seed_now
),
(
    'instructor-approved-default',
    @seed_member_id_base + 2,
    'INSTRUCTOR_APPROVED',
    @seed_now,
    @seed_now
);

SET @vivaldi_resort_id = (
    SELECT id
    FROM resorts
    WHERE code = 'VIVALDI_PARK'
);

INSERT INTO instructor_profiles (
    member_id,
    resort_id,
    real_name,
    phone,
    gender,
    birth_date,
    intro,
    career_start_date,
    level,
    certificate_type,
    experience,
    approval_status,
    approved_at,
    created_at,
    updated_at
) VALUES (
    @seed_member_id_base + 2,
    @vivaldi_resort_id,
    '가격검증강사',
    '010-0000-0000',
    'MALE',
    '1994-02-24',
    '비발디파크 가격 흐름을 검증하는 로컬 전용 강사입니다.',
    '2026-01-15',
    1,
    NULL,
    0,
    'APPROVED',
    '2026-01-15 11:00:00.000000',
    @seed_now,
    @seed_now
);

SET @instructor_profile_id = LAST_INSERT_ID();

INSERT INTO instructor_profile_certificates (
    instructor_profile_id,
    certificate_type
) VALUES (
    @instructor_profile_id,
    'KSIA_SKI_LEVEL_1'
);

COMMIT;
