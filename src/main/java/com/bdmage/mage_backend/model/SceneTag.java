package com.bdmage.mage_backend.model;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "scene_tags")
@IdClass(SceneTagId.class)
public class SceneTag {

	@Id
	@Column(name = "scene_id", nullable = false)
	private Long sceneId;

	@Id
	@Column(name = "tag_id", nullable = false)
	private Long tagId;

	protected SceneTag() {
	}

	public SceneTag(Long sceneId, Long tagId) {
		this.sceneId = Objects.requireNonNull(sceneId, "sceneId must not be null");
		this.tagId = Objects.requireNonNull(tagId, "tagId must not be null");
	}

	public Long getSceneId() {
		return this.sceneId;
	}

	public Long getTagId() {
		return this.tagId;
	}
}
