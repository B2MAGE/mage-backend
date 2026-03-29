package com.bdmage.mage_backend.model;

import java.io.Serializable;
import java.util.Objects;

public class PresetTagId implements Serializable {

	private static final long serialVersionUID = 1L;

	private Long presetId;

	private Long tagId;

	public PresetTagId() {
	}

	public PresetTagId(Long presetId, Long tagId) {
		this.presetId = presetId;
		this.tagId = tagId;
	}

	public Long getPresetId() {
		return this.presetId;
	}

	public Long getTagId() {
		return this.tagId;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof PresetTagId that)) {
			return false;
		}
		return Objects.equals(this.presetId, that.presetId)
				&& Objects.equals(this.tagId, that.tagId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.presetId, this.tagId);
	}
}
