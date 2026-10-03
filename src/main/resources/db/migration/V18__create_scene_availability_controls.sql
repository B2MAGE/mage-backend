CREATE TABLE scene_availability_controls (
    scene_id BIGINT PRIMARY KEY REFERENCES scenes(id) ON DELETE CASCADE,
    disabled BOOLEAN NOT NULL DEFAULT FALSE,
    changed_by_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    changed_at TIMESTAMPTZ,
    reason VARCHAR(1000),
    CONSTRAINT scene_availability_audit_check CHECK (
        (changed_at IS NULL AND changed_by_user_id IS NULL AND reason IS NULL)
        OR (changed_at IS NOT NULL AND reason IS NOT NULL AND LENGTH(TRIM(reason)) > 0)
    ),
    CONSTRAINT scene_disabled_requires_audit CHECK (NOT disabled OR changed_at IS NOT NULL)
);

CREATE TABLE custom_rendering_control (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    changed_by_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    changed_at TIMESTAMPTZ,
    reason VARCHAR(1000),
    CONSTRAINT custom_rendering_audit_check CHECK (
        (changed_at IS NULL AND changed_by_user_id IS NULL AND reason IS NULL)
        OR (changed_at IS NOT NULL AND reason IS NOT NULL AND LENGTH(TRIM(reason)) > 0)
    ),
    CONSTRAINT custom_enabled_requires_audit CHECK (NOT enabled OR changed_at IS NOT NULL)
);

INSERT INTO custom_rendering_control (id, enabled) VALUES (1, FALSE);
