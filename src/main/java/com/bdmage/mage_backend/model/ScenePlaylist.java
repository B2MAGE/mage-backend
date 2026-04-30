package com.bdmage.mage_backend.model;

import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "scene_playlists")
@IdClass(ScenePlaylistId.class)
public class ScenePlaylist {

	@Id
	@Column(name = "scene_id", nullable = false)
	private Long sceneId;

	@Id
	@Column(name = "playlist_id", nullable = false)
	private Long playlistId;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	protected ScenePlaylist() {
	}

	public ScenePlaylist(Long sceneId, Long playlistId) {
		this.sceneId = Objects.requireNonNull(sceneId, "sceneId must not be null");
		this.playlistId = Objects.requireNonNull(playlistId, "playlistId must not be null");
	}

	public Long getSceneId() {
		return this.sceneId;
	}

	public Long getPlaylistId() {
		return this.playlistId;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}
}
