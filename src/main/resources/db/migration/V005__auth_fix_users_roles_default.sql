-- Restore a safe default for the legacy users.roles column so inserts that omit it still succeed.
UPDATE users
SET roles = 'USER'
WHERE roles IS NULL;

ALTER TABLE users
    ALTER COLUMN roles SET DEFAULT 'USER';

ALTER TABLE users
    ALTER COLUMN roles SET NOT NULL;
