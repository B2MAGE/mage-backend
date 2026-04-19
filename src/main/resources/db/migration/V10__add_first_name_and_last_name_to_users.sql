ALTER TABLE users
    ADD COLUMN first_name VARCHAR(100),
    ADD COLUMN last_name VARCHAR(100);

-- Preserve existing public attribution while backfilling structured name fields.
UPDATE users
SET first_name = display_name,
    last_name = ''
WHERE first_name IS NULL
   OR last_name IS NULL;

ALTER TABLE users
    ALTER COLUMN first_name SET NOT NULL,
    ALTER COLUMN last_name SET NOT NULL;
