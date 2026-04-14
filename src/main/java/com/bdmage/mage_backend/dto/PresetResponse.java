package com.bdmage.mage_backend.dto;

import java.time.Instant;
import java.util.Map;

import com.bdmage.mage_backend.model.Preset;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public record PresetResponse(
		Long presetId,
		Long ownerUserId,
		String creatorDisplayName,
		String name,
		Map<String, Object> sceneData,
		String thumbnailRef,
		Instant createdAt) {

	private static final ObjectMapper JSON_OBJECT_MAPPER = new ObjectMapper();
	private static final TypeReference<Map<String, Object>> SCENE_DATA_TYPE = new TypeReference<>() {
	};

	public static PresetResponse from(Preset preset, String creatorDisplayName) {
		return new PresetResponse(
				preset.getId(),
				preset.getOwnerUserId(),
				creatorDisplayName,
				preset.getName(),
				JSON_OBJECT_MAPPER.convertValue(preset.getSceneData(), SCENE_DATA_TYPE),
				preset.getThumbnailRef(),
				preset.getCreatedAt());
	}
}
