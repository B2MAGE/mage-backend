package com.bdmage.mage_backend.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.bdmage.mage_backend.model.Scene;
import com.bdmage.mage_backend.model.User;
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
		SceneEngagementResponse engagement) {

	private static final ObjectMapper JSON_OBJECT_MAPPER = new ObjectMapper();
	private static final TypeReference<Map<String, Object>> SCENE_DATA_TYPE = new TypeReference<>() {
	};

	public static SceneResponse from(Scene scene, String creatorDisplayName) {
		return from(scene, creatorDisplayName, null, List.of(), SceneEngagementResponse.empty());
	}

	public static SceneResponse from(Scene scene, String creatorDisplayName, List<String> tags) {
		return from(scene, creatorDisplayName, null, tags, SceneEngagementResponse.empty());
	}

	public static SceneResponse from(
			Scene scene,
			String creatorDisplayName,
			List<String> tags,
			SceneEngagementResponse engagement) {
		return from(scene, creatorDisplayName, null, tags, engagement);
	}

	public static SceneResponse from(
			Scene scene,
			String creatorDisplayName,
			String creatorHandle,
			List<String> tags,
			SceneEngagementResponse engagement) {
		return from(scene, creatorDisplayName, creatorHandle,
				User.DEFAULT_AVATAR_GRADIENT_START, User.DEFAULT_AVATAR_GRADIENT_END, tags, engagement);
	}

	public static SceneResponse from(
			Scene scene,
			String creatorDisplayName,
			String creatorHandle,
			String creatorAvatarGradientStart,
			String creatorAvatarGradientEnd,
			List<String> tags,
			SceneEngagementResponse engagement) {
		return new SceneResponse(
				scene.getId(),
				scene.getOwnerUserId(),
				creatorDisplayName,
				creatorHandle,
				creatorAvatarGradientStart,
				creatorAvatarGradientEnd,
				scene.getName(),
				scene.getDescription(),
				JSON_OBJECT_MAPPER.convertValue(scene.getSceneData(), SCENE_DATA_TYPE),
				scene.getThumbnailRef(),
				scene.getCreatedAt(),
				List.copyOf(tags),
				engagement != null ? engagement : SceneEngagementResponse.empty());
	}
}
