package com.bdmage.mage_backend.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.bdmage.mage_backend.model.Scene;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public record SceneResponse(
		Long sceneId,
		Long ownerUserId,
		String creatorDisplayName,
		String creatorHandle,
		String creatorAvatarGradientStart,
		String creatorAvatarGradientEnd,
		String name,
		String description,
		Map<String, Object> sceneData,
		String thumbnailRef,
		Instant createdAt,
		List<String> tags,
		SceneEngagementResponse engagement,
		SceneAvailabilityResponse availability) {

	private static final ObjectMapper JSON_OBJECT_MAPPER = new ObjectMapper();
	private static final TypeReference<Map<String, Object>> SCENE_DATA_TYPE = new TypeReference<>() {};

	public SceneResponse {
		Objects.requireNonNull(availability, "availability must be checked before returning a scene");
		if (!availability.available()) sceneData = null;
	}

	public static SceneResponse from(
			Scene scene,
			String creatorDisplayName,
			String creatorHandle,
			String creatorAvatarGradientStart,
			String creatorAvatarGradientEnd,
			List<String> tags,
			SceneEngagementResponse engagement,
			SceneAvailabilityResponse availability) {
		Objects.requireNonNull(availability, "availability must be checked before returning a scene");
		return new SceneResponse(
				scene.getId(), scene.getOwnerUserId(), creatorDisplayName, creatorHandle,
				creatorAvatarGradientStart, creatorAvatarGradientEnd,
				scene.getName(), scene.getDescription(),
				availability.available() ? JSON_OBJECT_MAPPER.convertValue(scene.getSceneData(), SCENE_DATA_TYPE) : null,
				scene.getThumbnailRef(), scene.getCreatedAt(), List.copyOf(tags),
				engagement != null ? engagement : SceneEngagementResponse.empty(), availability);
	}
}
