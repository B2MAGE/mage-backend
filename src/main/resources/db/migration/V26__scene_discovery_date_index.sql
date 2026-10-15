-- Both chronological discovery directions use the same stable date/ID ordering.
CREATE INDEX scenes_created_at_id_idx ON scenes (created_at DESC, id DESC);
