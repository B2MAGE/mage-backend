package com.bdmage.mage_backend.model;

import java.io.Serializable;
import java.util.Objects;

public class ScenePlaylistId implements Serializable {

	private static final long serialVersionUID = 1L;

	private Long sceneId;

	private Long playlistId;

	public ScenePlaylistId() {
	}

	public ScenePlaylistId(Long sceneId, Long playlistId) {
		this.sceneId = sceneId;
		this.playlistId = playlistId;
	}

	public Long getSceneId() {
		return this.sceneId;
	}

	public Long getPlaylistId() {
		return this.playlistId;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof ScenePlaylistId that)) {
			return false;
		}
		return Objects.equals(this.sceneId, that.sceneId)
				&& Objects.equals(this.playlistId, that.playlistId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.sceneId, this.playlistId);
	}
}
