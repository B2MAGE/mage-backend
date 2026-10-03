package com.bdmage.mage_backend.dto;

import java.util.Map;

import com.bdmage.mage_backend.model.Scene;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Explicit owner-only source access for editing; this payload must not start playback. */
public record SceneRepairResponse(
		Long sceneId,
		Long ownerUserId,
		String name,
		String description,
		Map<String, Object> sceneData,
		String thumbnailRef,
		SceneAvailabilityResponse availability,
		boolean playable) {

	private static final ObjectMapper JSON_OBJECT_MAPPER = new ObjectMapper();
	private static final TypeReference<Map<String, Object>> SCENE_DATA_TYPE = new TypeReference<>() {
	};

	public static SceneRepairResponse from(Scene scene, SceneAvailabilityResponse availability) {
		return new SceneRepairResponse(scene.getId(), scene.getOwnerUserId(), scene.getName(),
				scene.getDescription(), JSON_OBJECT_MAPPER.convertValue(scene.getSceneData(), SCENE_DATA_TYPE),
				scene.getThumbnailRef(), availability, false);
	}
}
