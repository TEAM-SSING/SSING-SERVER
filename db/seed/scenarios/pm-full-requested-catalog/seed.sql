-- Local/CI SNAPSHOT only. Source identities and raw persona narratives are intentionally not copied.
START TRANSACTION;

SET @seed_now = UTC_TIMESTAMP(6);
SET @catalog_member_id_base = CAST(UNIX_TIMESTAMP(@seed_now) * 1000000 AS UNSIGNED) + 100;

INSERT INTO members (
    id,
    nickname,
    profile_image_url,
    role,
    status,
    created_at,
    updated_at
) VALUES
    (@catalog_member_id_base + 1, '목데이터소비자01', NULL, 'CONSUMER', 'ACTIVE', @seed_now, @seed_now),
    (@catalog_member_id_base + 2, '목데이터소비자02', NULL, 'CONSUMER', 'ACTIVE', @seed_now, @seed_now),
    (@catalog_member_id_base + 3, '목데이터소비자03', NULL, 'CONSUMER', 'ACTIVE', @seed_now, @seed_now),
    (@catalog_member_id_base + 4, '목데이터소비자04', NULL, 'CONSUMER', 'ACTIVE', @seed_now, @seed_now),
    (@catalog_member_id_base + 5, '목데이터소비자05', NULL, 'CONSUMER', 'ACTIVE', @seed_now, @seed_now),
    (@catalog_member_id_base + 6, '목데이터소비자07', NULL, 'CONSUMER', 'ACTIVE', @seed_now, @seed_now),
    (@catalog_member_id_base + 7, '목데이터소비자08', NULL, 'CONSUMER', 'ACTIVE', @seed_now, @seed_now),
    (@catalog_member_id_base + 8, '목데이터소비자09', NULL, 'CONSUMER', 'ACTIVE', @seed_now, @seed_now),
    (@catalog_member_id_base + 9, '목데이터강사01', NULL, 'INSTRUCTOR', 'ACTIVE', @seed_now, @seed_now),
    (@catalog_member_id_base + 10, '목데이터강사02', NULL, 'INSTRUCTOR', 'ACTIVE', @seed_now, @seed_now),
    (@catalog_member_id_base + 11, '목데이터강사03', NULL, 'INSTRUCTOR', 'ACTIVE', @seed_now, @seed_now);

INSERT INTO dev_personas (
    persona_key,
    member_id,
    template,
    created_at,
    updated_at
) VALUES
    ('pm-consumer-001', @catalog_member_id_base + 1, 'GENERAL_CONSUMER', @seed_now, @seed_now),
    ('pm-consumer-002', @catalog_member_id_base + 2, 'GENERAL_CONSUMER', @seed_now, @seed_now),
    ('pm-consumer-003', @catalog_member_id_base + 3, 'GENERAL_CONSUMER', @seed_now, @seed_now),
    ('pm-consumer-004', @catalog_member_id_base + 4, 'GENERAL_CONSUMER', @seed_now, @seed_now),
    ('pm-consumer-005', @catalog_member_id_base + 5, 'GENERAL_CONSUMER', @seed_now, @seed_now),
    ('pm-consumer-007', @catalog_member_id_base + 6, 'GENERAL_CONSUMER', @seed_now, @seed_now),
    ('pm-consumer-008', @catalog_member_id_base + 7, 'GENERAL_CONSUMER', @seed_now, @seed_now),
    ('pm-consumer-009', @catalog_member_id_base + 8, 'GENERAL_CONSUMER', @seed_now, @seed_now),
    ('pm-instructor-approved-001', @catalog_member_id_base + 9, 'INSTRUCTOR_APPROVED', @seed_now, @seed_now),
    ('pm-instructor-approved-002', @catalog_member_id_base + 10, 'INSTRUCTOR_APPROVED', @seed_now, @seed_now),
    ('pm-instructor-approved-003', @catalog_member_id_base + 11, 'INSTRUCTOR_APPROVED', @seed_now, @seed_now);

SET @high1_resort_id = (SELECT id FROM resorts WHERE code = 'HIGH1');
SET @konjiam_resort_id = (SELECT id FROM resorts WHERE code = 'KONJIAM_RESORT');
SET @vivaldi_resort_id = (SELECT id FROM resorts WHERE code = 'VIVALDI_PARK');

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
        @catalog_member_id_base + 9,
        @high1_resort_id,
        '목데이터강사01',
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
        @catalog_member_id_base + 10,
        @high1_resort_id,
        '목데이터강사02',
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
        @catalog_member_id_base + 11,
        @konjiam_resort_id,
        '목데이터강사03',
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
    );

SET @instructor_profile_001_id = (
    SELECT profile.id
    FROM instructor_profiles profile
    JOIN dev_personas persona ON persona.member_id = profile.member_id
    WHERE persona.persona_key = 'pm-instructor-approved-001'
);
SET @instructor_profile_002_id = (
    SELECT profile.id
    FROM instructor_profiles profile
    JOIN dev_personas persona ON persona.member_id = profile.member_id
    WHERE persona.persona_key = 'pm-instructor-approved-002'
);
SET @instructor_profile_003_id = (
    SELECT profile.id
    FROM instructor_profiles profile
    JOIN dev_personas persona ON persona.member_id = profile.member_id
    WHERE persona.persona_key = 'pm-instructor-approved-003'
);
SET @instructor_profile_004_id = (
    SELECT profile.id
    FROM instructor_profiles profile
    JOIN dev_personas persona ON persona.member_id = profile.member_id
    WHERE persona.persona_key = 'instructor-approved-default'
);

INSERT INTO instructor_profile_certificates (
    instructor_profile_id,
    certificate_type
) VALUES
    (@instructor_profile_001_id, 'KSIA_SKI_LEVEL_1'),
    (@instructor_profile_002_id, 'KSIA_SKI_LEVEL_1'),
    (@instructor_profile_003_id, 'KSIA_SKI_LEVEL_2');

INSERT INTO instructor_matching_settings (
    instructor_profile_id,
    sport,
    max_headcount,
    is_equipment_ready,
    is_exposed,
    created_at,
    updated_at
) VALUES
    (@instructor_profile_001_id, 'SKI', 2, b'1', b'1', @seed_now, @seed_now),
    (@instructor_profile_002_id, 'SKI', 5, b'1', b'1', @seed_now, @seed_now),
    (@instructor_profile_003_id, 'SKI', 2, b'1', b'1', @seed_now, @seed_now),
    (@instructor_profile_004_id, 'SKI', 3, b'1', b'1', @seed_now, @seed_now);

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

SET @member_consumer_001_id = (SELECT member_id FROM dev_personas WHERE persona_key = 'pm-consumer-001');
SET @member_consumer_002_id = (SELECT member_id FROM dev_personas WHERE persona_key = 'pm-consumer-002');
SET @member_consumer_003_id = (SELECT member_id FROM dev_personas WHERE persona_key = 'pm-consumer-003');
SET @member_consumer_004_id = (SELECT member_id FROM dev_personas WHERE persona_key = 'pm-consumer-004');
SET @member_consumer_005_id = (SELECT member_id FROM dev_personas WHERE persona_key = 'pm-consumer-005');
SET @member_consumer_006_id = (SELECT member_id FROM dev_personas WHERE persona_key = 'consumer-default');
SET @member_consumer_007_id = (SELECT member_id FROM dev_personas WHERE persona_key = 'pm-consumer-007');
SET @member_consumer_008_id = (SELECT member_id FROM dev_personas WHERE persona_key = 'pm-consumer-008');
SET @member_consumer_009_id = (SELECT member_id FROM dev_personas WHERE persona_key = 'pm-consumer-009');

SET @phoenix_resort_id = (SELECT id FROM resorts WHERE code = 'PHOENIX_PARK');
SET @jisan_resort_id = (SELECT id FROM resorts WHERE code = 'JISAN_FOREST_RESORT');
SET @alpensia_resort_id = (SELECT id FROM resorts WHERE code = 'ALPENSIA');
SET @oak_resort_id = (SELECT id FROM resorts WHERE code = 'OAK_VALLEY');
SET @elysian_resort_id = (SELECT id FROM resorts WHERE code = 'ELYSIAN_GANGCHON');
SET @welli_hilli_resort_id = (SELECT id FROM resorts WHERE code = 'WELLI_HILLI_PARK');

INSERT INTO matching_requests (
    headcount, is_equipment_ready, canceled_at, created_at, expires_at, matching_offer_id,
    member_id, resort_id, updated_at, lesson_level, sport, status, status_reason
) VALUES (2, b'1', NULL, @seed_now, NULL, NULL, @member_consumer_001_id, @high1_resort_id, @seed_now, 'BEGINNER', 'SKI', 'REQUESTED', NULL);
SET @request_001_a_id = LAST_INSERT_ID();

INSERT INTO matching_requests (
    headcount, is_equipment_ready, canceled_at, created_at, expires_at, matching_offer_id,
    member_id, resort_id, updated_at, lesson_level, sport, status, status_reason
) VALUES (4, b'1', NULL, @seed_now, NULL, NULL, @member_consumer_002_id, @phoenix_resort_id, @seed_now, 'BEGINNER', 'SNOWBOARD', 'REQUESTED', NULL);
SET @request_002_a_id = LAST_INSERT_ID();

INSERT INTO matching_requests (
    headcount, is_equipment_ready, canceled_at, created_at, expires_at, matching_offer_id,
    member_id, resort_id, updated_at, lesson_level, sport, status, status_reason
) VALUES (1, b'0', NULL, @seed_now, NULL, NULL, @member_consumer_003_id, @konjiam_resort_id, @seed_now, 'FIRST_TIME', 'SKI', 'REQUESTED', NULL);
SET @request_003_a_id = LAST_INSERT_ID();

INSERT INTO matching_requests (
    headcount, is_equipment_ready, canceled_at, created_at, expires_at, matching_offer_id,
    member_id, resort_id, updated_at, lesson_level, sport, status, status_reason
) VALUES (1, b'1', NULL, @seed_now, NULL, NULL, @member_consumer_004_id, @jisan_resort_id, @seed_now, 'INTERMEDIATE', 'SNOWBOARD', 'REQUESTED', NULL);
SET @request_004_a_id = LAST_INSERT_ID();

INSERT INTO matching_requests (
    headcount, is_equipment_ready, canceled_at, created_at, expires_at, matching_offer_id,
    member_id, resort_id, updated_at, lesson_level, sport, status, status_reason
) VALUES (1, b'1', NULL, @seed_now, NULL, NULL, @member_consumer_005_id, @alpensia_resort_id, @seed_now, 'CERTIFIED', 'SKI', 'REQUESTED', NULL);
SET @request_005_a_id = LAST_INSERT_ID();

INSERT INTO matching_requests (
    headcount, is_equipment_ready, canceled_at, created_at, expires_at, matching_offer_id,
    member_id, resort_id, updated_at, lesson_level, sport, status, status_reason
) VALUES (1, b'1', NULL, @seed_now, NULL, NULL, @member_consumer_006_id, @vivaldi_resort_id, @seed_now, 'FIRST_TIME', 'SKI', 'REQUESTED', NULL);
SET @request_006_a_id = LAST_INSERT_ID();

INSERT INTO matching_requests (
    headcount, is_equipment_ready, canceled_at, created_at, expires_at, matching_offer_id,
    member_id, resort_id, updated_at, lesson_level, sport, status, status_reason
) VALUES (5, b'1', NULL, @seed_now, NULL, NULL, @member_consumer_007_id, @oak_resort_id, @seed_now, 'BEGINNER', 'SKI', 'REQUESTED', NULL);
SET @request_007_a_id = LAST_INSERT_ID();

INSERT INTO matching_requests (
    headcount, is_equipment_ready, canceled_at, created_at, expires_at, matching_offer_id,
    member_id, resort_id, updated_at, lesson_level, sport, status, status_reason
) VALUES (5, b'1', NULL, @seed_now, NULL, NULL, @member_consumer_007_id, @oak_resort_id, @seed_now, 'BEGINNER', 'SNOWBOARD', 'REQUESTED', NULL);
SET @request_007_b_id = LAST_INSERT_ID();

INSERT INTO matching_requests (
    headcount, is_equipment_ready, canceled_at, created_at, expires_at, matching_offer_id,
    member_id, resort_id, updated_at, lesson_level, sport, status, status_reason
) VALUES (3, b'1', NULL, @seed_now, NULL, NULL, @member_consumer_007_id, @oak_resort_id, @seed_now, 'INTERMEDIATE', 'SKI', 'REQUESTED', NULL);
SET @request_007_c_id = LAST_INSERT_ID();

INSERT INTO matching_requests (
    headcount, is_equipment_ready, canceled_at, created_at, expires_at, matching_offer_id,
    member_id, resort_id, updated_at, lesson_level, sport, status, status_reason
) VALUES (3, b'1', NULL, @seed_now, NULL, NULL, @member_consumer_007_id, @oak_resort_id, @seed_now, 'INTERMEDIATE', 'SNOWBOARD', 'REQUESTED', NULL);
SET @request_007_d_id = LAST_INSERT_ID();

INSERT INTO matching_requests (
    headcount, is_equipment_ready, canceled_at, created_at, expires_at, matching_offer_id,
    member_id, resort_id, updated_at, lesson_level, sport, status, status_reason
) VALUES (5, b'0', NULL, @seed_now, NULL, NULL, @member_consumer_008_id, @elysian_resort_id, @seed_now, 'FIRST_TIME', 'SKI', 'REQUESTED', NULL);
SET @request_008_a_id = LAST_INSERT_ID();

INSERT INTO matching_requests (
    headcount, is_equipment_ready, canceled_at, created_at, expires_at, matching_offer_id,
    member_id, resort_id, updated_at, lesson_level, sport, status, status_reason
) VALUES (5, b'0', NULL, @seed_now, NULL, NULL, @member_consumer_008_id, @elysian_resort_id, @seed_now, 'FIRST_TIME', 'SKI', 'REQUESTED', NULL);
SET @request_008_b_id = LAST_INSERT_ID();

INSERT INTO matching_requests (
    headcount, is_equipment_ready, canceled_at, created_at, expires_at, matching_offer_id,
    member_id, resort_id, updated_at, lesson_level, sport, status, status_reason
) VALUES (4, b'1', NULL, @seed_now, NULL, NULL, @member_consumer_008_id, @elysian_resort_id, @seed_now, 'BEGINNER', 'SKI', 'REQUESTED', NULL);
SET @request_008_c_id = LAST_INSERT_ID();

INSERT INTO matching_requests (
    headcount, is_equipment_ready, canceled_at, created_at, expires_at, matching_offer_id,
    member_id, resort_id, updated_at, lesson_level, sport, status, status_reason
) VALUES (4, b'1', NULL, @seed_now, NULL, NULL, @member_consumer_008_id, @elysian_resort_id, @seed_now, 'BEGINNER', 'SKI', 'REQUESTED', NULL);
SET @request_008_d_id = LAST_INSERT_ID();

INSERT INTO matching_requests (
    headcount, is_equipment_ready, canceled_at, created_at, expires_at, matching_offer_id,
    member_id, resort_id, updated_at, lesson_level, sport, status, status_reason
) VALUES (2, b'1', NULL, @seed_now, NULL, NULL, @member_consumer_009_id, @welli_hilli_resort_id, @seed_now, 'BEGINNER', 'SKI', 'REQUESTED', NULL);
SET @request_009_a_id = LAST_INSERT_ID();

INSERT INTO matching_requests (
    headcount, is_equipment_ready, canceled_at, created_at, expires_at, matching_offer_id,
    member_id, resort_id, updated_at, lesson_level, sport, status, status_reason
) VALUES (3, b'1', NULL, @seed_now, NULL, NULL, @member_consumer_009_id, @welli_hilli_resort_id, @seed_now, 'BEGINNER', 'SNOWBOARD', 'REQUESTED', NULL);
SET @request_009_b_id = LAST_INSERT_ID();

INSERT INTO matching_requests_requested_duration_minutes (
    matching_request_id,
    duration_minutes
) VALUES
    (@request_001_a_id, 240),
    (@request_002_a_id, 180),
    (@request_003_a_id, 120),
    (@request_004_a_id, 180),
    (@request_005_a_id, 180),
    (@request_006_a_id, 120),
    (@request_007_a_id, 180),
    (@request_007_b_id, 180),
    (@request_007_c_id, 180),
    (@request_007_d_id, 180),
    (@request_008_a_id, 120),
    (@request_008_b_id, 120),
    (@request_008_c_id, 120),
    (@request_008_d_id, 120),
    (@request_009_a_id, 180),
    (@request_009_b_id, 180);

INSERT INTO matching_request_participants (
    age,
    created_at,
    matching_request_id,
    updated_at,
    gender
) VALUES
    (8, @seed_now, @request_001_a_id, @seed_now, 'MALE'),
    (7, @seed_now, @request_001_a_id, @seed_now, 'FEMALE'),
    (27, @seed_now, @request_002_a_id, @seed_now, 'MALE'),
    (27, @seed_now, @request_002_a_id, @seed_now, 'MALE'),
    (26, @seed_now, @request_002_a_id, @seed_now, 'FEMALE'),
    (28, @seed_now, @request_002_a_id, @seed_now, 'MALE'),
    (31, @seed_now, @request_003_a_id, @seed_now, 'FEMALE'),
    (35, @seed_now, @request_004_a_id, @seed_now, 'MALE'),
    (24, @seed_now, @request_005_a_id, @seed_now, 'FEMALE'),
    (10, @seed_now, @request_006_a_id, @seed_now, 'MALE'),
    (29, @seed_now, @request_007_a_id, @seed_now, 'MALE'),
    (31, @seed_now, @request_007_a_id, @seed_now, 'FEMALE'),
    (32, @seed_now, @request_007_a_id, @seed_now, 'MALE'),
    (28, @seed_now, @request_007_a_id, @seed_now, 'FEMALE'),
    (35, @seed_now, @request_007_a_id, @seed_now, 'MALE'),
    (34, @seed_now, @request_007_b_id, @seed_now, 'FEMALE'),
    (30, @seed_now, @request_007_b_id, @seed_now, 'MALE'),
    (33, @seed_now, @request_007_b_id, @seed_now, 'FEMALE'),
    (27, @seed_now, @request_007_b_id, @seed_now, 'MALE'),
    (36, @seed_now, @request_007_b_id, @seed_now, 'FEMALE'),
    (38, @seed_now, @request_007_c_id, @seed_now, 'MALE'),
    (26, @seed_now, @request_007_c_id, @seed_now, 'FEMALE'),
    (29, @seed_now, @request_007_c_id, @seed_now, 'MALE'),
    (31, @seed_now, @request_007_d_id, @seed_now, 'FEMALE'),
    (34, @seed_now, @request_007_d_id, @seed_now, 'MALE'),
    (37, @seed_now, @request_007_d_id, @seed_now, 'FEMALE'),
    (7, @seed_now, @request_008_a_id, @seed_now, 'MALE'),
    (7, @seed_now, @request_008_a_id, @seed_now, 'FEMALE'),
    (8, @seed_now, @request_008_a_id, @seed_now, 'MALE'),
    (8, @seed_now, @request_008_a_id, @seed_now, 'FEMALE'),
    (9, @seed_now, @request_008_a_id, @seed_now, 'MALE'),
    (9, @seed_now, @request_008_b_id, @seed_now, 'FEMALE'),
    (10, @seed_now, @request_008_b_id, @seed_now, 'MALE'),
    (10, @seed_now, @request_008_b_id, @seed_now, 'FEMALE'),
    (8, @seed_now, @request_008_b_id, @seed_now, 'MALE'),
    (9, @seed_now, @request_008_b_id, @seed_now, 'FEMALE'),
    (7, @seed_now, @request_008_c_id, @seed_now, 'MALE'),
    (10, @seed_now, @request_008_c_id, @seed_now, 'FEMALE'),
    (8, @seed_now, @request_008_c_id, @seed_now, 'MALE'),
    (9, @seed_now, @request_008_c_id, @seed_now, 'FEMALE'),
    (10, @seed_now, @request_008_d_id, @seed_now, 'MALE'),
    (7, @seed_now, @request_008_d_id, @seed_now, 'FEMALE'),
    (8, @seed_now, @request_008_d_id, @seed_now, 'MALE'),
    (9, @seed_now, @request_008_d_id, @seed_now, 'FEMALE'),
    (30, @seed_now, @request_009_a_id, @seed_now, 'MALE'),
    (29, @seed_now, @request_009_a_id, @seed_now, 'FEMALE'),
    (31, @seed_now, @request_009_b_id, @seed_now, 'MALE'),
    (30, @seed_now, @request_009_b_id, @seed_now, 'FEMALE'),
    (32, @seed_now, @request_009_b_id, @seed_now, 'MALE');

COMMIT;
