CREATE TABLE playlists (
  id BIGSERIAL PRIMARY KEY,
  owner_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name VARCHAR(100) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX playlists_owner_user_id_idx ON playlists (owner_user_id);

CREATE TABLE scene_playlists (
  scene_id BIGINT NOT NULL REFERENCES scenes(id) ON DELETE CASCADE,
  playlist_id BIGINT NOT NULL REFERENCES playlists(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (scene_id, playlist_id)
);

CREATE INDEX scene_playlists_playlist_id_idx ON scene_playlists (playlist_id);
