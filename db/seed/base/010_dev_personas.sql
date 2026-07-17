-- Local/dev/CI QA identities. Matching state never belongs in this file.
-- Familiar Korean names are memory aids only; every profile value is synthetic.
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
    (@seed_member_id_base + 1, '대뜸 GOAT 성빈', NULL, 'CONSUMER', 'ACTIVE', @seed_now, @seed_now),
    (@seed_member_id_base + 2, '보법 다른 유정', NULL, 'INSTRUCTOR', 'ACTIVE', @seed_now, @seed_now),
    (@seed_member_id_base + 3, '폭룡적 예지', NULL, 'CONSUMER', 'ACTIVE', @seed_now, @seed_now),
    (@seed_member_id_base + 4, '느좋 그 자체 예림', NULL, 'CONSUMER', 'ACTIVE', @seed_now, @seed_now),
    (@seed_member_id_base + 5, '감다살 유빈', NULL, 'CONSUMER', 'ACTIVE', @seed_now, @seed_now),
    (@seed_member_id_base + 6, '야르 선문', NULL, 'CONSUMER', 'ACTIVE', @seed_now, @seed_now),
    (@seed_member_id_base + 7, '난리자베스 채원', NULL, 'CONSUMER', 'ACTIVE', @seed_now, @seed_now),
    (@seed_member_id_base + 8, '도파민 풀충 나현', NULL, 'CONSUMER', 'ACTIVE', @seed_now, @seed_now),
    (@seed_member_id_base + 9, '레전드 갱신 중인 지환', NULL, 'CONSUMER', 'ACTIVE', @seed_now, @seed_now),
    (@seed_member_id_base + 10, '갑차기스러운 예슬', NULL, 'CONSUMER', 'ACTIVE', @seed_now, @seed_now),
    (@seed_member_id_base + 11, '기세로 다 해먹는 도연', NULL, 'INSTRUCTOR', 'ACTIVE', @seed_now, @seed_now),
    (@seed_member_id_base + 12, '폼 미친 성빈', NULL, 'INSTRUCTOR', 'ACTIVE', @seed_now, @seed_now),
    (@seed_member_id_base + 13, '뉴런 공유 중인 유정', NULL, 'INSTRUCTOR', 'ACTIVE', @seed_now, @seed_now),
    (@seed_member_id_base + 14, '냅다 레전드 유빈', NULL, 'CONSUMER', 'ACTIVE', @seed_now, @seed_now);

INSERT INTO dev_personas (
    persona_key,
    member_id,
    template,
    created_at,
    updated_at
) VALUES
    ('대뜸GOAT-성빈-일반강습생', @seed_member_id_base + 1, 'GENERAL_CONSUMER', @seed_now, @seed_now),
    ('보법다른-유정-승인강사', @seed_member_id_base + 2, 'INSTRUCTOR_APPROVED', @seed_now, @seed_now),
    ('폭룡적-예지-일반강습생', @seed_member_id_base + 3, 'GENERAL_CONSUMER', @seed_now, @seed_now),
    ('느좋그자체-예림-일반강습생', @seed_member_id_base + 4, 'GENERAL_CONSUMER', @seed_now, @seed_now),
    ('감다살-유빈-일반강습생', @seed_member_id_base + 5, 'GENERAL_CONSUMER', @seed_now, @seed_now),
    ('야르-선문-일반강습생', @seed_member_id_base + 6, 'GENERAL_CONSUMER', @seed_now, @seed_now),
    ('난리자베스-채원-일반강습생', @seed_member_id_base + 7, 'GENERAL_CONSUMER', @seed_now, @seed_now),
    ('도파민풀충-나현-일반강습생', @seed_member_id_base + 8, 'GENERAL_CONSUMER', @seed_now, @seed_now),
    ('레전드갱신중인-지환-일반강습생', @seed_member_id_base + 9, 'GENERAL_CONSUMER', @seed_now, @seed_now),
    ('갑차기스러운-예슬-일반강습생', @seed_member_id_base + 10, 'GENERAL_CONSUMER', @seed_now, @seed_now),
    ('기세로다해먹는-도연-승인강사', @seed_member_id_base + 11, 'INSTRUCTOR_APPROVED', @seed_now, @seed_now),
    ('폼미친-성빈-승인강사', @seed_member_id_base + 12, 'INSTRUCTOR_APPROVED', @seed_now, @seed_now),
    ('뉴런공유중인-유정-승인강사', @seed_member_id_base + 13, 'INSTRUCTOR_APPROVED', @seed_now, @seed_now),
    ('냅다레전드-유빈-일반강습생', @seed_member_id_base + 14, 'GENERAL_CONSUMER', @seed_now, @seed_now);

COMMIT;
