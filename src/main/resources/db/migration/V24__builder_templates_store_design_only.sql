-- Pre-review personal templates were whole-scene snapshots. Retain their design and image,
-- and invalidate stale editors only where removing scene-wide settings changes the document.
UPDATE builder_templates
SET scene_data = scene_data - 'parameters' - 'settings',
    revision = revision + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE scene_data ?| ARRAY['parameters', 'settings'];

ALTER TABLE builder_templates ADD CONSTRAINT builder_templates_design_only
    CHECK (NOT (scene_data ?| ARRAY['parameters', 'settings']));
