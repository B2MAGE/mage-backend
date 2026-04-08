ALTER TABLE users
    DROP CONSTRAINT users_auth_provider_check,
    DROP CONSTRAINT users_auth_provider_fields_check,
    DROP CONSTRAINT users_email_auth_provider_key;

WITH local_google_pairs AS (
    SELECT
        local_user.id AS local_user_id,
        google_user.id AS google_user_id,
        google_user.google_subject AS google_subject,
        LEAST(local_user.created_at, google_user.created_at) AS merged_created_at
    FROM users local_user
    JOIN users google_user
      ON google_user.email = local_user.email
     AND local_user.auth_provider = 'LOCAL'
     AND google_user.auth_provider = 'GOOGLE'
)
UPDATE auth_tokens auth_token
SET user_id = local_google_pairs.local_user_id
FROM local_google_pairs
WHERE auth_token.user_id = local_google_pairs.google_user_id;

WITH local_google_pairs AS (
    SELECT
        local_user.id AS local_user_id,
        google_user.id AS google_user_id
    FROM users local_user
    JOIN users google_user
      ON google_user.email = local_user.email
     AND local_user.auth_provider = 'LOCAL'
     AND google_user.auth_provider = 'GOOGLE'
)
UPDATE presets preset
SET owner_user_id = local_google_pairs.local_user_id
FROM local_google_pairs
WHERE preset.owner_user_id = local_google_pairs.google_user_id;

WITH local_google_pairs AS (
    SELECT
        local_user.id AS local_user_id,
        google_user.id AS google_user_id,
        google_user.google_subject AS google_subject,
        LEAST(local_user.created_at, google_user.created_at) AS merged_created_at
    FROM users local_user
    JOIN users google_user
      ON google_user.email = local_user.email
     AND local_user.auth_provider = 'LOCAL'
     AND google_user.auth_provider = 'GOOGLE'
)
UPDATE users local_user
SET auth_provider = 'LOCAL_GOOGLE',
    google_subject = local_google_pairs.google_subject,
    created_at = local_google_pairs.merged_created_at
FROM local_google_pairs
WHERE local_user.id = local_google_pairs.local_user_id;

WITH merged_google_accounts AS (
    SELECT google_user.id AS google_user_id
    FROM users linked_user
    JOIN users google_user
      ON google_user.email = linked_user.email
     AND linked_user.auth_provider = 'LOCAL_GOOGLE'
     AND google_user.auth_provider = 'GOOGLE'
)
DELETE FROM users google_user
USING merged_google_accounts
WHERE google_user.id = merged_google_accounts.google_user_id;

ALTER TABLE users
    ADD CONSTRAINT users_email_key UNIQUE (email),
    ADD CONSTRAINT users_auth_provider_check CHECK (auth_provider IN ('LOCAL', 'GOOGLE', 'LOCAL_GOOGLE')),
    ADD CONSTRAINT users_auth_provider_fields_check CHECK (
        (auth_provider = 'LOCAL' AND password_hash IS NOT NULL AND google_subject IS NULL)
        OR (auth_provider = 'GOOGLE' AND password_hash IS NULL AND google_subject IS NOT NULL)
        OR (auth_provider = 'LOCAL_GOOGLE' AND password_hash IS NOT NULL AND google_subject IS NOT NULL)
    );
