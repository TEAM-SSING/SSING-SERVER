-- Preflight before deployment:
-- SELECT member_id, COUNT(*), GROUP_CONCAT(id ORDER BY created_at, id)
-- FROM matching_requests
-- WHERE status IN ('REQUESTED', 'GROUPED', 'MATCHED')
-- GROUP BY member_id
-- HAVING COUNT(*) > 1;
--
-- Do not choose or cancel a legacy duplicate automatically. Resolve every returned request first.
-- MySQL unique indexes allow multiple NULL values, so only active negotiations occupy the member key.
ALTER TABLE matching_requests
    ADD COLUMN active_negotiation_member_id BIGINT
        GENERATED ALWAYS AS (
            CASE
                WHEN status IN ('REQUESTED', 'GROUPED', 'MATCHED') THEN member_id
                ELSE NULL
            END
        ) STORED,
    ADD CONSTRAINT uk_matching_requests_active_negotiation_member
        UNIQUE (active_negotiation_member_id);
