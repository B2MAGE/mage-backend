ALTER TABLE scenes
    ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC';

ALTER TABLE scenes
    ADD CONSTRAINT scenes_visibility_check
    CHECK (visibility IN ('PUBLIC', 'PRIVATE', 'UNLISTED', 'DRAFT'));