-- 문자열 비교와 별개로 실제 저장 바이트가 UTF-8인지 검증한다.
CREATE TEMPORARY TABLE seed_utf8_contract_assertion (
    ok TINYINT NOT NULL,
    CONSTRAINT chk_seed_utf8_contract_assertion CHECK (ok = 1)
);

INSERT INTO seed_utf8_contract_assertion (ok)
SELECT (
    (SELECT HEX(name) FROM resorts WHERE code = 'HIGH1')
        = 'ED9598EC9DB4EC9B90EBA6ACECA1B0ED8AB8'
    AND
    (SELECT HEX(member.nickname)
     FROM members member
     JOIN dev_personas persona ON persona.member_id = member.id
     WHERE persona.persona_key = '대뜸GOAT-성빈-일반강습생')
        = 'EB8C80EB9CB820474F415420EC84B1EBB988'
);

DROP TEMPORARY TABLE seed_utf8_contract_assertion;
