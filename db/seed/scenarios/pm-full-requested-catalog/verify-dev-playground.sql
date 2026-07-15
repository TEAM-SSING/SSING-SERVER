-- qa-free-consumer는 자유 QA 시작점이며 기존 요청을 소유하지 않는다.
CREATE TEMPORARY TABLE dev_playground_contract_assertion (
    ok TINYINT NOT NULL,
    CONSTRAINT chk_dev_playground_contract_assertion CHECK (ok = 1)
);

INSERT INTO dev_playground_contract_assertion (ok)
SELECT (
    (SELECT COUNT(*)
     FROM dev_personas persona
     JOIN members member ON member.id = persona.member_id
     WHERE persona.persona_key = 'qa-free-consumer'
       AND persona.template = 'GENERAL_CONSUMER'
       AND member.role = 'CONSUMER'
       AND member.status = 'ACTIVE'
       AND HEX(member.nickname) = '514120EC9E90EC9CA020EC868CEBB984EC9E90') = 1
    AND
    (SELECT COUNT(*)
     FROM matching_requests request
     JOIN dev_personas persona ON persona.member_id = request.member_id
     WHERE persona.persona_key = 'qa-free-consumer') = 0
    AND
    (SELECT COUNT(*)
     FROM oauth_accounts account
     JOIN dev_personas persona ON persona.member_id = account.member_id
     WHERE persona.persona_key = 'qa-free-consumer') = 0
    AND
    (SELECT COUNT(*)
     FROM refresh_tokens token
     JOIN dev_personas persona ON persona.member_id = token.member_id
     WHERE persona.persona_key = 'qa-free-consumer') = 0
);

DROP TEMPORARY TABLE dev_playground_contract_assertion;
