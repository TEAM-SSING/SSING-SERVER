-- MVP pricing requires exactly one active 0% platform fee policy in every environment.
START TRANSACTION;

SET @migration_now = UTC_TIMESTAMP(6);

UPDATE platform_fee_policies
SET is_active = b'0',
    updated_at = @migration_now
WHERE is_active = b'1';

INSERT INTO platform_fee_policies (
    fee_rate_bps,
    is_active,
    created_at,
    updated_at
) VALUES (
    0,
    b'1',
    @migration_now,
    @migration_now
);

COMMIT;
