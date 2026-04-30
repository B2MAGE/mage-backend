package com.bdmage.mage_backend.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.bdmage.mage_backend.dto.SceneDetailResponse;
import com.bdmage.mage_backend.dto.SceneEngagementResponse;
import com.bdmage.mage_backend.dto.SceneResponse;
import com.bdmage.mage_backend.model.Scene;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class SceneResponseFactory {

	private static final String UNKNOWN_CREATOR_DISPLAY_NAME = "Unknown creator";

	private final UserRepository userRepository;
	private final SceneEngagementService sceneEngagementService;

	public SceneResponseFactory(UserRepository userRepository, SceneEngagementService sceneEngagementService) {
		this.userRepository = userRepository;
		this.sceneEngagementService = sceneEngagementService;
	}

	public SceneResponse from(Scene scene) {
		return SceneResponse.from(scene, resolveCreatorDisplayName(scene), List.of(), resolveEngagement(scene, null));
	}

	public SceneResponse from(Scene scene, List<String> tags) {
		return SceneResponse.from(scene, resolveCreatorDisplayName(scene), tags, resolveEngagement(scene, null));
	}

	public SceneDetailResponse detailFrom(
			Scene scene,
			List<String> tags,
			SceneEngagementResponse engagement) {
		return SceneDetailResponse.from(scene, resolveCreatorDisplayName(scene), tags, engagement);
	}

	public List<SceneResponse> from(List<Scene> scenes) {
		return from(scenes, null);
	}

	public List<SceneResponse> from(List<Scene> scenes, Long currentUserId) {
		Map<Long, String> creatorDisplayNames = resolveCreatorDisplayNames(scenes);

		return scenes.stream()
				.map(scene -> SceneResponse.from(
						scene,
						creatorDisplayNames.getOrDefault(scene.getOwnerUserId(), UNKNOWN_CREATOR_DISPLAY_NAME),
						List.of(),
						resolveEngagement(scene, currentUserId)))
				.toList();
	}

	private SceneEngagementResponse resolveEngagement(Scene scene, Long currentUserId) {
		if (scene.getId() == null) {
			return SceneEngagementResponse.empty();
		}

		SceneEngagementResponse engagement = this.sceneEngagementService.getSceneEngagement(scene.getId(), currentUserId);
		return engagement != null ? engagement : SceneEngagementResponse.empty();
	}

	private Map<Long, String> resolveCreatorDisplayNames(List<Scene> scenes) {
		Set<Long> ownerUserIds = scenes.stream()
				.map(Scene::getOwnerUserId)
				.collect(java.util.stream.Collectors.toSet());

		return this.userRepository.findAllById(ownerUserIds).stream()
				.collect(java.util.stream.Collectors.toMap(User::getId, User::getDisplayName));
	}

	private String resolveCreatorDisplayName(Scene scene) {
		return this.userRepository.findById(scene.getOwnerUserId())
				.map(User::getDisplayName)
				.orElse(UNKNOWN_CREATOR_DISPLAY_NAME);
	}

}
