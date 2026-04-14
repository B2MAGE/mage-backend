ALTER TABLE presets
    RENAME TO scenes;

ALTER TABLE scenes
    RENAME CONSTRAINT presets_pkey TO scenes_pkey;

ALTER TABLE scenes
    RENAME CONSTRAINT presets_owner_user_id_fkey TO scenes_owner_user_id_fkey;

ALTER INDEX presets_owner_user_id_idx
    RENAME TO scenes_owner_user_id_idx;

ALTER SEQUENCE presets_id_seq
    RENAME TO scenes_id_seq;

ALTER TABLE preset_tags
    RENAME TO scene_tags;

ALTER TABLE scene_tags
    RENAME COLUMN preset_id TO scene_id;

ALTER TABLE scene_tags
    RENAME CONSTRAINT preset_tags_pkey TO scene_tags_pkey;

ALTER TABLE scene_tags
    RENAME CONSTRAINT preset_tags_preset_id_fkey TO scene_tags_scene_id_fkey;

ALTER TABLE scene_tags
    RENAME CONSTRAINT preset_tags_tag_id_fkey TO scene_tags_tag_id_fkey;

ALTER INDEX preset_tags_tag_id_idx
    RENAME TO scene_tags_tag_id_idx;
