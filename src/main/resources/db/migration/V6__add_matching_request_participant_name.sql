ALTER TABLE matching_request_participants
    ADD COLUMN name VARCHAR(50) NULL AFTER matching_request_id;
