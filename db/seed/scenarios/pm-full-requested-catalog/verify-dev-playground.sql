-- 자유 QA 페르소나는 기존 요청을 소유하지 않는 시작점이다.
CREATE TEMPORARY TABLE dev_playground_contract_assertion (
    ok TINYINT NOT NULL,
    CONSTRAINT chk_dev_playground_contract_assertion CHECK (ok = 1)
);

INSERT INTO dev_playground_contract_assertion (ok)
SELECT (
    (SELECT COUNT(*)
     FROM dev_personas persona
     JOIN members member ON member.id = persona.member_id
     WHERE persona.persona_key = '냅다레전드-유빈-자유QA-강습생'
       AND persona.template = 'GENERAL_CONSUMER'
       AND member.role = 'CONSUMER'
       AND member.status = 'ACTIVE'
       AND HEX(member.nickname) = 'EB8385EB8BA420EBA088ECA084EB939C20EC9CA0EBB988') = 1
    AND
    (SELECT COUNT(*)
     FROM matching_requests request
     JOIN dev_personas persona ON persona.member_id = request.member_id
     WHERE persona.persona_key = '냅다레전드-유빈-자유QA-강습생') = 0
    AND
    (SELECT COUNT(*)
     FROM oauth_accounts account
     JOIN dev_personas persona ON persona.member_id = account.member_id
     WHERE persona.persona_key = '냅다레전드-유빈-자유QA-강습생') = 0
    AND
    (SELECT COUNT(*)
     FROM refresh_tokens token
     JOIN dev_personas persona ON persona.member_id = token.member_id
     WHERE persona.persona_key = '냅다레전드-유빈-자유QA-강습생') = 0
);

DROP TEMPORARY TABLE dev_playground_contract_assertion;
