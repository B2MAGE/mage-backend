package com.bdmage.mage_backend.model;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "preset_tags")
@IdClass(PresetTagId.class)
public class PresetTag {

	@Id
	@Column(name = "preset_id", nullable = false)
	private Long presetId;

	@Id
	@Column(name = "tag_id", nullable = false)
	private Long tagId;

	protected PresetTag() {
	}

	public PresetTag(Long presetId, Long tagId) {
		this.presetId = Objects.requireNonNull(presetId, "presetId must not be null");
		this.tagId = Objects.requireNonNull(tagId, "tagId must not be null");
	}

	public Long getPresetId() {
		return this.presetId;
	}

	public Long getTagId() {
		return this.tagId;
	}
}
