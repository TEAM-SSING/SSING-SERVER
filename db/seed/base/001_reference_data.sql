-- Source: PM sheet resorts!A4:C12 plus the final API contract's Base-only resort catalog.
-- O2/Muju pass fees are synthetic local/dev fixture values, not confirmed operating prices.
START TRANSACTION;

SET @seed_now = UTC_TIMESTAMP(6);

INSERT INTO resorts (
    code,
    name,
    display_name,
    pass_fee_amount,
    created_at,
    updated_at
) VALUES
    ('HIGH1', '하이원리조트', '하이원리조트', 0, @seed_now, @seed_now),
    ('PHOENIX_PARK', '휘닉스파크', '휘닉스파크', 30000, @seed_now, @seed_now),
    ('KONJIAM_RESORT', '곤지암리조트', '곤지암리조트', 35000, @seed_now, @seed_now),
    ('JISAN_FOREST_RESORT', '지산포레스트리조트', '지산포레스트리조트', 30000, @seed_now, @seed_now),
    ('ALPENSIA', '알펜시아', '알펜시아', 30000, @seed_now, @seed_now),
    ('VIVALDI_PARK', '비발디파크', '비발디파크', 25000, @seed_now, @seed_now),
    ('OAK_VALLEY', '오크밸리', '오크밸리', 30000, @seed_now, @seed_now),
    ('ELYSIAN_GANGCHON', '엘리시안 강촌', '엘리시안 강촌', 35000, @seed_now, @seed_now),
    ('WELLI_HILLI_PARK', '웰리힐리파크', '웰리힐리파크', 30000, @seed_now, @seed_now),
    ('O2_RESORT', '오투리조트', '오투리조트', 30000, @seed_now, @seed_now),
    ('MUJU_DEOGYUSAN_RESORT', '무주덕유산리조트', '무주덕유산리조트', 30000, @seed_now, @seed_now);

COMMIT;
