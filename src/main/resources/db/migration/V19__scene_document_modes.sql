-- Preserve every original document. Only a validated future write can upgrade a row.
ALTER TABLE scenes ADD COLUMN scene_mode VARCHAR(20) NOT NULL DEFAULT 'legacy-custom';

ALTER TABLE scenes ADD CONSTRAINT scenes_document_mode_check CHECK ((
    scene_mode = 'legacy-custom'
    OR (scene_mode = 'custom-v1'
        AND scene_data @> '{"schemaVersion":1,"kind":"custom"}'::jsonb
        AND jsonb_typeof(scene_data->'scene') = 'object')
    OR (scene_mode = 'template-v1'
        AND scene_data @> '{"schemaVersion":1,"kind":"template"}'::jsonb
        AND jsonb_typeof(scene_data->'templateId') = 'string'
        AND scene_data @> '{"templateVersion":1}'::jsonb)
) IS TRUE);
