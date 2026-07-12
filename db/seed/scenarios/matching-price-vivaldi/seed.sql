-- FLOW preparation only: the active matching request is created through the API/service.
START TRANSACTION;

SET @seed_now = UTC_TIMESTAMP(6);
SET @instructor_profile_id = (
    SELECT profile.id
    FROM instructor_profiles profile
    JOIN dev_personas persona ON persona.member_id = profile.member_id
    WHERE persona.persona_key = 'instructor-approved-default'
);

INSERT INTO instructor_matching_settings (
    instructor_profile_id,
    sport,
    max_headcount,
    is_equipment_ready,
    is_exposed,
    created_at,
    updated_at
) VALUES (
    @instructor_profile_id,
    'SKI',
    3,
    b'1',
    b'1',
    @seed_now,
    @seed_now
);

SET @instructor_matching_setting_id = LAST_INSERT_ID();

INSERT INTO instructor_matching_settings_lesson_levels (
    instructor_matching_setting_id,
    lesson_level
) VALUES
    (@instructor_matching_setting_id, 'FIRST_TIME'),
    (@instructor_matching_setting_id, 'BEGINNER');

INSERT INTO instructor_matching_settings_available_durations (
    instructor_matching_setting_id,
    available_duration_minutes
) VALUES (
    @instructor_matching_setting_id,
    120
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
