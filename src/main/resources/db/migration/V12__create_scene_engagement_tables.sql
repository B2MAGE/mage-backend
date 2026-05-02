CREATE TABLE scene_views (
  id BIGSERIAL PRIMARY KEY,
  scene_id BIGINT NOT NULL REFERENCES scenes(id) ON DELETE CASCADE,
  user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
  viewed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX scene_views_scene_id_idx ON scene_views (scene_id);
CREATE INDEX scene_views_user_id_idx ON scene_views (user_id);

CREATE TABLE scene_votes (
  scene_id BIGINT NOT NULL REFERENCES scenes(id) ON DELETE CASCADE,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  vote_value SMALLINT NOT NULL CHECK (vote_value IN (-1, 1)),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (scene_id, user_id)
);

CREATE INDEX scene_votes_scene_id_vote_value_idx ON scene_votes (scene_id, vote_value);
CREATE INDEX scene_votes_user_id_idx ON scene_votes (user_id);

CREATE TABLE scene_saves (
  scene_id BIGINT NOT NULL REFERENCES scenes(id) ON DELETE CASCADE,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (scene_id, user_id)
);

CREATE INDEX scene_saves_user_id_idx ON scene_saves (user_id);
