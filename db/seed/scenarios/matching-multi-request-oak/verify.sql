CREATE TEMPORARY TABLE seed_contract_assertion (
    ok TINYINT NOT NULL,
    CONSTRAINT chk_seed_contract_assertion CHECK (ok = 1)
);

INSERT INTO seed_contract_assertion (ok)
SELECT (
    (SELECT COUNT(*) FROM resorts WHERE code = 'OAK_VALLEY' AND pass_fee_amount = 30000) = 1
    AND
    (SELECT COUNT(*)
     FROM dev_personas persona
     JOIN members member ON member.id = persona.member_id
     WHERE persona.persona_key = 'pm-consumer-007'
       AND persona.template = 'GENERAL_CONSUMER'
       AND member.role = 'CONSUMER'
       AND member.status = 'ACTIVE') = 1
    AND
    (SELECT COUNT(*) FROM instructor_matching_settings WHERE is_exposed = b'1') = 0
    AND
    (SELECT COUNT(*) FROM matching_requests) = 0
);

DROP TEMPORARY TABLE seed_contract_assertion;
