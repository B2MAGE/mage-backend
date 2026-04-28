-- Stores time-limited, single-use tokens for the password reset flow.
-- We only keep the hash of the raw token so a database breach can't be
-- used to immediately reset anyone's password.
CREATE TABLE password_reset_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used        BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX password_reset_tokens_user_id_idx ON password_reset_tokens (user_id);
