package com.bdmage.mage_backend.model;

import java.io.Serializable;
import java.util.Objects;

public class SceneTagId implements Serializable {

	private static final long serialVersionUID = 1L;

	private Long sceneId;

	private Long tagId;

	public SceneTagId() {
	}

	public SceneTagId(Long sceneId, Long tagId) {
		this.sceneId = sceneId;
		this.tagId = tagId;
	}

	public Long getSceneId() {
		return this.sceneId;
	}

	public Long getTagId() {
		return this.tagId;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof SceneTagId that)) {
			return false;
		}
		return Objects.equals(this.sceneId, that.sceneId)
				&& Objects.equals(this.tagId, that.tagId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.sceneId, this.tagId);
	}
}
