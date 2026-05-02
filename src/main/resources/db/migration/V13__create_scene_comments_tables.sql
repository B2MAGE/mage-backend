CREATE TABLE scene_comments (
  id BIGSERIAL PRIMARY KEY,
  scene_id BIGINT NOT NULL REFERENCES scenes(id) ON DELETE CASCADE,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  parent_comment_id BIGINT REFERENCES scene_comments(id) ON DELETE CASCADE,
  body VARCHAR(2000) NOT NULL CHECK (char_length(trim(body)) > 0),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX scene_comments_scene_id_parent_comment_id_idx
  ON scene_comments (scene_id, parent_comment_id, created_at, id);

CREATE INDEX scene_comments_user_id_idx ON scene_comments (user_id);

CREATE TABLE scene_comment_votes (
  comment_id BIGINT NOT NULL REFERENCES scene_comments(id) ON DELETE CASCADE,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  vote_value SMALLINT NOT NULL CHECK (vote_value IN (-1, 1)),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (comment_id, user_id)
);

CREATE INDEX scene_comment_votes_comment_id_vote_value_idx
  ON scene_comment_votes (comment_id, vote_value);

CREATE INDEX scene_comment_votes_user_id_idx ON scene_comment_votes (user_id);
