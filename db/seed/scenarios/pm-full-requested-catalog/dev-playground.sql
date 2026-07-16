-- Dev QA playground overlay. PM 원본 snapshot 검증이 끝난 뒤에만 적용한다.
START TRANSACTION;

SET @qa_seed_now = UTC_TIMESTAMP(6);

INSERT INTO members (
    nickname,
    profile_image_url,
    role,
    status,
    created_at,
    updated_at
) VALUES (
    '냅다 레전드 유빈',
    NULL,
    'CONSUMER',
    'ACTIVE',
    @qa_seed_now,
    @qa_seed_now
);

SET @qa_free_consumer_member_id = LAST_INSERT_ID();

INSERT INTO dev_personas (
    persona_key,
    member_id,
    template,
    created_at,
    updated_at
) VALUES (
    '냅다레전드-유빈-자유QA-강습생',
    @qa_free_consumer_member_id,
    'GENERAL_CONSUMER',
    @qa_seed_now,
    @qa_seed_now
);

COMMIT;
