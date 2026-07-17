-- FLOW preparation only: expose the shared Vivaldi instructor.
-- Matching requests, snapshots, payments, and lessons are created through the API/service.
START TRANSACTION;

SET @seed_now = UTC_TIMESTAMP(6);

UPDATE instructor_matching_settings setting
JOIN instructor_profiles profile ON profile.id = setting.instructor_profile_id
JOIN dev_personas persona ON persona.member_id = profile.member_id
SET setting.is_exposed = b'1',
    setting.updated_at = @seed_now
WHERE persona.persona_key = '보법다른-유정-승인강사';

COMMIT;
