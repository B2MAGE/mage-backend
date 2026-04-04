package com.bdmage.mage_backend.dto;

import com.bdmage.mage_backend.model.Preset;

public record UserPresetResponse(
		Long id,
		String name) {

	public static UserPresetResponse from(Preset preset) {
		return new UserPresetResponse(
				preset.getId(),
				preset.getName());
	}
}
