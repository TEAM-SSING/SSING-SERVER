-- Local/CI SNAPSHOT only. Common QA personas and instructor foundations come from idle base.
-- This scenario owns only PM exposure and matching request history.
START TRANSACTION;

SET @seed_now = UTC_TIMESTAMP(6);
SET @high1_resort_id = (SELECT id FROM resorts WHERE code = 'HIGH1');
SET @konjiam_resort_id = (SELECT id FROM resorts WHERE code = 'KONJIAM_RESORT');
SET @vivaldi_resort_id = (SELECT id FROM resorts WHERE code = 'VIVALDI_PARK');

UPDATE instructor_matching_settings setting
JOIN instructor_profiles profile ON profile.id = setting.instructor_profile_id
JOIN dev_personas persona ON persona.member_id = profile.member_id
SET setting.is_exposed = b'1',
    setting.updated_at = @seed_now
WHERE persona.persona_key IN (
    '기세로다해먹는-도연-승인강사',
    '폼미친-성빈-승인강사',
    '뉴런공유중인-유정-승인강사',
    '보법다른-유정-승인강사'
);

SET @member_consumer_001_id = (SELECT member_id FROM dev_personas WHERE persona_key = '폭룡적-예지-일반강습생');
SET @member_consumer_002_id = (SELECT member_id FROM dev_personas WHERE persona_key = '느좋그자체-예림-일반강습생');
SET @member_consumer_003_id = (SELECT member_id FROM dev_personas WHERE persona_key = '감다살-유빈-일반강습생');
SET @member_consumer_004_id = (SELECT member_id FROM dev_personas WHERE persona_key = '야르-선문-일반강습생');
SET @member_consumer_005_id = (SELECT member_id FROM dev_personas WHERE persona_key = '난리자베스-채원-일반강습생');
SET @member_consumer_006_id = (SELECT member_id FROM dev_personas WHERE persona_key = '대뜸GOAT-성빈-일반강습생');
SET @member_consumer_007_id = (SELECT member_id FROM dev_personas WHERE persona_key = '도파민풀충-나현-일반강습생');
SET @member_consumer_008_id = (SELECT member_id FROM dev_personas WHERE persona_key = '레전드갱신중인-지환-일반강습생');
SET @member_consumer_009_id = (SELECT member_id FROM dev_personas WHERE persona_key = '갑차기스러운-예슬-일반강습생');

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
) VALUES (5, b'1', @seed_now, @seed_now, NULL, NULL, @member_consumer_007_id, @oak_resort_id, @seed_now, 'BEGINNER', 'SNOWBOARD', 'CANCELED', 'CONSUMER_CANCELED');
SET @request_007_b_id = LAST_INSERT_ID();

INSERT INTO matching_requests (
    headcount, is_equipment_ready, canceled_at, created_at, expires_at, matching_offer_id,
    member_id, resort_id, updated_at, lesson_level, sport, status, status_reason
) VALUES (3, b'1', @seed_now, @seed_now, NULL, NULL, @member_consumer_007_id, @oak_resort_id, @seed_now, 'INTERMEDIATE', 'SKI', 'CANCELED', 'CONSUMER_CANCELED');
SET @request_007_c_id = LAST_INSERT_ID();

INSERT INTO matching_requests (
    headcount, is_equipment_ready, canceled_at, created_at, expires_at, matching_offer_id,
    member_id, resort_id, updated_at, lesson_level, sport, status, status_reason
) VALUES (3, b'1', @seed_now, @seed_now, NULL, NULL, @member_consumer_007_id, @oak_resort_id, @seed_now, 'INTERMEDIATE', 'SNOWBOARD', 'CANCELED', 'CONSUMER_CANCELED');
SET @request_007_d_id = LAST_INSERT_ID();

INSERT INTO matching_requests (
    headcount, is_equipment_ready, canceled_at, created_at, expires_at, matching_offer_id,
    member_id, resort_id, updated_at, lesson_level, sport, status, status_reason
) VALUES (5, b'0', NULL, @seed_now, NULL, NULL, @member_consumer_008_id, @elysian_resort_id, @seed_now, 'FIRST_TIME', 'SKI', 'REQUESTED', NULL);
SET @request_008_a_id = LAST_INSERT_ID();

INSERT INTO matching_requests (
    headcount, is_equipment_ready, canceled_at, created_at, expires_at, matching_offer_id,
    member_id, resort_id, updated_at, lesson_level, sport, status, status_reason
) VALUES (5, b'0', @seed_now, @seed_now, NULL, NULL, @member_consumer_008_id, @elysian_resort_id, @seed_now, 'FIRST_TIME', 'SKI', 'CANCELED', 'CONSUMER_CANCELED');
SET @request_008_b_id = LAST_INSERT_ID();

INSERT INTO matching_requests (
    headcount, is_equipment_ready, canceled_at, created_at, expires_at, matching_offer_id,
    member_id, resort_id, updated_at, lesson_level, sport, status, status_reason
) VALUES (4, b'1', @seed_now, @seed_now, NULL, NULL, @member_consumer_008_id, @elysian_resort_id, @seed_now, 'BEGINNER', 'SKI', 'CANCELED', 'CONSUMER_CANCELED');
SET @request_008_c_id = LAST_INSERT_ID();

INSERT INTO matching_requests (
    headcount, is_equipment_ready, canceled_at, created_at, expires_at, matching_offer_id,
    member_id, resort_id, updated_at, lesson_level, sport, status, status_reason
) VALUES (4, b'1', @seed_now, @seed_now, NULL, NULL, @member_consumer_008_id, @elysian_resort_id, @seed_now, 'BEGINNER', 'SKI', 'CANCELED', 'CONSUMER_CANCELED');
SET @request_008_d_id = LAST_INSERT_ID();

INSERT INTO matching_requests (
    headcount, is_equipment_ready, canceled_at, created_at, expires_at, matching_offer_id,
    member_id, resort_id, updated_at, lesson_level, sport, status, status_reason
) VALUES (2, b'1', NULL, @seed_now, NULL, NULL, @member_consumer_009_id, @welli_hilli_resort_id, @seed_now, 'BEGINNER', 'SKI', 'REQUESTED', NULL);
SET @request_009_a_id = LAST_INSERT_ID();

INSERT INTO matching_requests (
    headcount, is_equipment_ready, canceled_at, created_at, expires_at, matching_offer_id,
    member_id, resort_id, updated_at, lesson_level, sport, status, status_reason
) VALUES (3, b'1', @seed_now, @seed_now, NULL, NULL, @member_consumer_009_id, @welli_hilli_resort_id, @seed_now, 'BEGINNER', 'SNOWBOARD', 'CANCELED', 'CONSUMER_CANCELED');
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
