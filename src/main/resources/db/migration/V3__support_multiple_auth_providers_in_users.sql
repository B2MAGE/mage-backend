ALTER TABLE users
    ADD COLUMN auth_provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL',
    ADD COLUMN google_subject VARCHAR(255);

ALTER TABLE users
    ALTER COLUMN password_hash DROP NOT NULL;

ALTER TABLE users
    DROP CONSTRAINT users_email_key;

ALTER TABLE users
    ADD CONSTRAINT users_auth_provider_check CHECK (auth_provider IN ('LOCAL', 'GOOGLE')),
    ADD CONSTRAINT users_email_auth_provider_key UNIQUE (email, auth_provider),
    ADD CONSTRAINT users_google_subject_key UNIQUE (google_subject),
    ADD CONSTRAINT users_auth_provider_fields_check CHECK (
        (auth_provider = 'LOCAL' AND password_hash IS NOT NULL AND google_subject IS NULL)
        OR (auth_provider = 'GOOGLE' AND password_hash IS NULL AND google_subject IS NOT NULL)
    );
