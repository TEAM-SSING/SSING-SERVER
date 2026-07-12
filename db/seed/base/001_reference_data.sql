-- Source: PM sheet resorts!A9:C9. display_name is normalized from name.
START TRANSACTION;

SET @seed_now = UTC_TIMESTAMP(6);

INSERT INTO resorts (
    code,
    name,
    display_name,
    pass_fee_amount,
    created_at,
    updated_at
) VALUES (
    'VIVALDI_PARK',
    '비발디파크',
    '비발디파크',
    25000,
    @seed_now,
    @seed_now
);

COMMIT;
