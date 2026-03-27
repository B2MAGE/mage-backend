CREATE TABLE preset_tags (
  preset_id BIGINT NOT NULL REFERENCES presets(id) ON DELETE CASCADE,
  tag_id BIGINT NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
  PRIMARY KEY (preset_id, tag_id)
);

CREATE INDEX preset_tags_tag_id_idx ON preset_tags (tag_id);
