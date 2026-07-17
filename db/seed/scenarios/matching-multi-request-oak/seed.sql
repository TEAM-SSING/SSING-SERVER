-- FLOW preparation only. The shared consumer starts idle in base.
-- Four request histories are created and canceled through the API, so this scenario has no SQL delta.
START TRANSACTION;
COMMIT;
