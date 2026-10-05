-- Add a distinct data-only classification without rewriting any existing document.
ALTER TABLE scenes DROP CONSTRAINT scenes_document_mode_check;

ALTER TABLE scenes ADD CONSTRAINT scenes_document_mode_check CHECK ((
    scene_mode = 'legacy-custom'
    OR (scene_mode = 'custom-v1'
        AND scene_data @> '{"schemaVersion":1,"kind":"custom"}'::jsonb
        AND jsonb_typeof(scene_data->'scene') = 'object')
    OR (scene_mode = 'template-v1'
        AND scene_data @> '{"schemaVersion":1,"kind":"template"}'::jsonb
        AND jsonb_typeof(scene_data->'templateId') = 'string'
        AND scene_data @> '{"templateVersion":1}'::jsonb)
    OR (scene_mode = 'builder-v1'
        AND scene_data @> '{"schemaVersion":1,"kind":"builder","builderVersion":1}'::jsonb
        AND jsonb_typeof(scene_data->'objects') = 'array')
) IS TRUE);
