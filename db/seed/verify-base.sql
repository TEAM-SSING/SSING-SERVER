-- Migration 필수 데이터와 자동 Dev 배포에 필요한 최소 base seed를 함께 검증한다.
CREATE TEMPORARY TABLE base_seed_contract_assertion (
    ok TINYINT NOT NULL,
    CONSTRAINT chk_base_seed_contract_assertion CHECK (ok = 1)
);

INSERT INTO base_seed_contract_assertion (ok)
SELECT (
    (SELECT COUNT(*)
     FROM platform_fee_policies
     WHERE fee_rate_bps = 0
       AND is_active = b'1') = 1
    AND
    (SELECT COUNT(*)
     FROM resorts
     WHERE code IN (
         'HIGH1',
         'PHOENIX_PARK',
         'VIVALDI_PARK',
         'WELLI_HILLI_PARK',
         'ELYSIAN_GANGCHON',
         'OAK_VALLEY',
         'ALPENSIA',
         'O2_RESORT',
         'KONJIAM_RESORT',
         'JISAN_FOREST_RESORT',
         'MUJU_DEOGYUSAN_RESORT'
     )) = 11
    AND
    (SELECT COUNT(*)
     FROM dev_personas
     WHERE persona_key IN (
         '대뜸GOAT-성빈-비발디가격결제-강습생',
         '보법다른-유정-비발디가격결제-강사'
     )) = 2
);

DROP TEMPORARY TABLE base_seed_contract_assertion;
