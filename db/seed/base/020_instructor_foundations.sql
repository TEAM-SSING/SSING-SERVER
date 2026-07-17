-- Shared instructor foundations. Profiles are ready for QA, but matching exposure starts OFF.
-- Scenarios may change exposure or matching state; they must not recreate these rows.
START TRANSACTION;

SET @seed_now = UTC_TIMESTAMP(6);
SET @high1_resort_id = (SELECT id FROM resorts WHERE code = 'HIGH1');
SET @konjiam_resort_id = (SELECT id FROM resorts WHERE code = 'KONJIAM_RESORT');
SET @vivaldi_resort_id = (SELECT id FROM resorts WHERE code = 'VIVALDI_PARK');

SET @member_instructor_001_id = (
    SELECT member_id FROM dev_personas WHERE persona_key = '기세로다해먹는-도연-승인강사'
);
SET @member_instructor_002_id = (
    SELECT member_id FROM dev_personas WHERE persona_key = '폼미친-성빈-승인강사'
);
SET @member_instructor_003_id = (
    SELECT member_id FROM dev_personas WHERE persona_key = '뉴런공유중인-유정-승인강사'
);
SET @member_instructor_004_id = (
    SELECT member_id FROM dev_personas WHERE persona_key = '보법다른-유정-승인강사'
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
) VALUES
    (
        @member_instructor_001_id,
        @high1_resort_id,
        '기세로 다 해먹는 도연',
        '010-0000-0101',
        'FEMALE',
        '2000-01-01',
        'PM 구조화 값을 익명화한 하이원 초급 스키 강사 fixture입니다.',
        '2025-12-15',
        1,
        NULL,
        0,
        'APPROVED',
        '2026-01-03 09:00:00.000000',
        @seed_now,
        @seed_now
    ),
    (
        @member_instructor_002_id,
        @high1_resort_id,
        '폼 미친 성빈',
        '010-0000-0102',
        'MALE',
        '2000-01-02',
        'PM 구조화 값을 익명화한 하이원 스키 강사 fixture입니다.',
        '2024-12-10',
        1,
        NULL,
        0,
        'APPROVED',
        '2025-12-01 08:30:00.000000',
        @seed_now,
        @seed_now
    ),
    (
        @member_instructor_003_id,
        @konjiam_resort_id,
        '뉴런 공유 중인 유정',
        '010-0000-0103',
        'MALE',
        '2000-01-03',
        'PM 구조화 값을 익명화한 곤지암 중상급 스키 강사 fixture입니다.',
        '2014-12-01',
        1,
        NULL,
        0,
        'APPROVED',
        '2025-11-20 10:00:00.000000',
        @seed_now,
        @seed_now
    ),
    (
        @member_instructor_004_id,
        @vivaldi_resort_id,
        '보법 다른 유정',
        '010-0000-0000',
        'MALE',
        '2000-01-04',
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

SET @instructor_profile_001_id = (
    SELECT profile.id
    FROM instructor_profiles profile
    JOIN dev_personas persona ON persona.member_id = profile.member_id
    WHERE persona.persona_key = '기세로다해먹는-도연-승인강사'
);
SET @instructor_profile_002_id = (
    SELECT profile.id
    FROM instructor_profiles profile
    JOIN dev_personas persona ON persona.member_id = profile.member_id
    WHERE persona.persona_key = '폼미친-성빈-승인강사'
);
SET @instructor_profile_003_id = (
    SELECT profile.id
    FROM instructor_profiles profile
    JOIN dev_personas persona ON persona.member_id = profile.member_id
    WHERE persona.persona_key = '뉴런공유중인-유정-승인강사'
);
SET @instructor_profile_004_id = (
    SELECT profile.id
    FROM instructor_profiles profile
    JOIN dev_personas persona ON persona.member_id = profile.member_id
    WHERE persona.persona_key = '보법다른-유정-승인강사'
);

INSERT INTO instructor_profile_certificates (
    instructor_profile_id,
    certificate_type
) VALUES
    (@instructor_profile_001_id, 'KSIA_SKI_LEVEL_1'),
    (@instructor_profile_002_id, 'KSIA_SKI_LEVEL_1'),
    (@instructor_profile_003_id, 'KSIA_SKI_LEVEL_2'),
    (@instructor_profile_004_id, 'KSIA_SKI_LEVEL_1');

INSERT INTO instructor_matching_settings (
    instructor_profile_id,
    sport,
    max_headcount,
    is_equipment_ready,
    is_exposed,
    created_at,
    updated_at
) VALUES
    (@instructor_profile_001_id, 'SKI', 2, b'1', b'0', @seed_now, @seed_now),
    (@instructor_profile_002_id, 'SKI', 5, b'1', b'0', @seed_now, @seed_now),
    (@instructor_profile_003_id, 'SKI', 2, b'1', b'0', @seed_now, @seed_now),
    (@instructor_profile_004_id, 'SKI', 3, b'1', b'0', @seed_now, @seed_now);

SET @instructor_setting_001_id = (
    SELECT id FROM instructor_matching_settings WHERE instructor_profile_id = @instructor_profile_001_id
);
SET @instructor_setting_002_id = (
    SELECT id FROM instructor_matching_settings WHERE instructor_profile_id = @instructor_profile_002_id
);
SET @instructor_setting_003_id = (
    SELECT id FROM instructor_matching_settings WHERE instructor_profile_id = @instructor_profile_003_id
);
SET @instructor_setting_004_id = (
    SELECT id FROM instructor_matching_settings WHERE instructor_profile_id = @instructor_profile_004_id
);

INSERT INTO instructor_matching_settings_lesson_levels (
    instructor_matching_setting_id,
    lesson_level
) VALUES
    (@instructor_setting_001_id, 'FIRST_TIME'),
    (@instructor_setting_001_id, 'BEGINNER'),
    (@instructor_setting_002_id, 'FIRST_TIME'),
    (@instructor_setting_002_id, 'BEGINNER'),
    (@instructor_setting_002_id, 'INTERMEDIATE'),
    (@instructor_setting_003_id, 'INTERMEDIATE'),
    (@instructor_setting_003_id, 'CERTIFIED'),
    (@instructor_setting_004_id, 'FIRST_TIME'),
    (@instructor_setting_004_id, 'BEGINNER');

INSERT INTO instructor_matching_settings_available_durations (
    instructor_matching_setting_id,
    available_duration_minutes
) VALUES
    (@instructor_setting_001_id, 240),
    (@instructor_setting_002_id, 240),
    (@instructor_setting_003_id, 120),
    (@instructor_setting_004_id, 120);

INSERT INTO instructor_price_policies (
    instructor_profile_id,
    base_price_amount,
    additional_person_price_amount,
    is_active,
    created_at,
    updated_at
) VALUES
    (@instructor_profile_001_id, 60000, 20000, b'1', @seed_now, @seed_now),
    (@instructor_profile_002_id, 75000, 30000, b'1', @seed_now, @seed_now),
    (@instructor_profile_003_id, 200000, 50000, b'1', @seed_now, @seed_now),
    (@instructor_profile_004_id, 60000, 20000, b'1', @seed_now, @seed_now);

COMMIT;
