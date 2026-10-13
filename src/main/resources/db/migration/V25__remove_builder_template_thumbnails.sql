-- Personal templates are named Builder designs, with no preview image or upload lifecycle.
-- Scene thumbnail columns and storage tables are unchanged.
ALTER TABLE builder_templates DROP COLUMN thumbnail_ref;
DROP TABLE builder_template_uploads;
DROP TABLE builder_template_asset_cleanup;
