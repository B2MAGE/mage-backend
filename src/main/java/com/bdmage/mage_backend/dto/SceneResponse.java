package com.bdmage.mage_backend.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.bdmage.mage_backend.model.Scene;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public record SceneResponse(
		Long sceneId,
		Long ownerUserId,
		String creatorDisplayName,
		String name,
		String description,
		Map<String, Object> sceneData,
		String thumbnailRef,
		Instant createdAt,
		List<String> tags) {

	private static final ObjectMapper JSON_OBJECT_MAPPER = new ObjectMapper();
	private static final TypeReference<Map<String, Object>> SCENE_DATA_TYPE = new TypeReference<>() {
	};

	public static SceneResponse from(Scene scene, String creatorDisplayName) {
		return from(scene, creatorDisplayName, List.of());
	}

	public static SceneResponse from(Scene scene, String creatorDisplayName, List<String> tags) {
		return new SceneResponse(
				scene.getId(),
				scene.getOwnerUserId(),
				creatorDisplayName,
				scene.getName(),
				scene.getDescription(),
				JSON_OBJECT_MAPPER.convertValue(scene.getSceneData(), SCENE_DATA_TYPE),
				scene.getThumbnailRef(),
				scene.getCreatedAt(),
				List.copyOf(tags));
	}
}
