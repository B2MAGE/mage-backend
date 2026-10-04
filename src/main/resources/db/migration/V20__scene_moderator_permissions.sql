CREATE TABLE scene_moderator_permissions (
    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    revision BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0)
);

-- Keep immutable identity snapshots: deleting an account must not rewrite history.
CREATE TABLE scene_moderator_audit (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    administrator_user_id BIGINT,
    target_user_id BIGINT NOT NULL,
    previous_enabled BOOLEAN NOT NULL,
    enabled BOOLEAN NOT NULL,
    expected_revision BIGINT NOT NULL CHECK (expected_revision >= 0),
    revision BIGINT NOT NULL CHECK (revision >= 0),
    reason VARCHAR(1000) NOT NULL CHECK (length(trim(reason)) BETWEEN 1 AND 1000),
    request_id UUID NOT NULL,
    source VARCHAR(30) NOT NULL CHECK (source IN ('administrator', 'legacy-allowlist')),
    changed_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    display_name VARCHAR(100) NOT NULL,
    handle VARCHAR(30) NOT NULL,
    email VARCHAR(320) NOT NULL,
    CHECK ((source = 'administrator' AND administrator_user_id IS NOT NULL)
        OR (source = 'legacy-allowlist' AND administrator_user_id IS NULL)),
    UNIQUE (administrator_user_id, request_id)
);

CREATE FUNCTION reject_scene_moderator_audit_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Scene moderator audit history is append-only';
END;
$$;
CREATE TRIGGER scene_moderator_audit_immutable
    BEFORE UPDATE OR DELETE ON scene_moderator_audit
    FOR EACH ROW EXECUTE FUNCTION reject_scene_moderator_audit_change();

CREATE TABLE scene_moderator_bootstrap (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    completed_at TIMESTAMPTZ
);
INSERT INTO scene_moderator_bootstrap (id) VALUES (1);
