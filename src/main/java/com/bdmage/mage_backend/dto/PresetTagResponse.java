package com.bdmage.mage_backend.dto;

import com.bdmage.mage_backend.model.PresetTag;

public record PresetTagResponse(
		Long presetId,
		Long tagId) {

	public static PresetTagResponse from(PresetTag presetTag) {
		return new PresetTagResponse(presetTag.getPresetId(), presetTag.getTagId());
	}
}
