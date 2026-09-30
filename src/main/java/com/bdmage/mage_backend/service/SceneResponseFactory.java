package com.bdmage.mage_backend.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.bdmage.mage_backend.dto.SceneDetailResponse;
import com.bdmage.mage_backend.dto.SceneEngagementResponse;
import com.bdmage.mage_backend.dto.SceneResponse;
import com.bdmage.mage_backend.model.Scene;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.repository.UserRepository;
import com.bdmage.mage_backend.repository.SceneTagRepository;
import com.bdmage.mage_backend.repository.SceneTagNameProjection;
import org.springframework.stereotype.Service;

@Service
public class SceneResponseFactory {

	private static final String UNKNOWN_CREATOR_DISPLAY_NAME = "Unknown creator";

	private final UserRepository userRepository;
	private final SceneTagRepository sceneTagRepository;
	private final SceneEngagementService sceneEngagementService;

	public SceneResponseFactory(UserRepository userRepository, SceneEngagementService sceneEngagementService,
			SceneTagRepository sceneTagRepository) {
		this.userRepository = userRepository;
		this.sceneTagRepository = sceneTagRepository;
		this.sceneEngagementService = sceneEngagementService;
	}

	public SceneResponse from(Scene scene) {
		CreatorIdentity creator = resolveCreatorIdentity(scene);
		return SceneResponse.from(
				scene,
				creator.displayName(),
				creator.handle(),
				creator.avatarGradientStart(),
				creator.avatarGradientEnd(),
				List.of(),
				resolveEngagement(scene, null));
	}

	public SceneResponse from(Scene scene, List<String> tags) {
		CreatorIdentity creator = resolveCreatorIdentity(scene);
		return SceneResponse.from(
				scene,
				creator.displayName(),
				creator.handle(),
				creator.avatarGradientStart(),
				creator.avatarGradientEnd(),
				tags,
				resolveEngagement(scene, null));
	}

	public SceneDetailResponse detailFrom(
			Scene scene,
			List<String> tags,
			SceneEngagementResponse engagement) {
		CreatorIdentity creator = resolveCreatorIdentity(scene);
		return SceneDetailResponse.from(scene, creator.displayName(), creator.handle(),
				creator.avatarGradientStart(), creator.avatarGradientEnd(), tags, engagement);
	}

	public List<SceneResponse> from(List<Scene> scenes) {
		return from(scenes, null);
	}

	public List<SceneResponse> from(List<Scene> scenes, Long currentUserId) {
		if (scenes.isEmpty()) {
			return List.of();
		}
		Map<Long, CreatorIdentity> creators = resolveCreatorIdentities(scenes);
		List<Long> sceneIds = scenes.stream().map(Scene::getId).toList();
		Map<Long, List<String>> tagsBySceneId = this.sceneTagRepository.findTagNamesBySceneIds(sceneIds).stream()
				.collect(Collectors.groupingBy(SceneTagNameProjection::getSceneId,
						Collectors.mapping(SceneTagNameProjection::getTagName, Collectors.toList())));

		return scenes.stream()
				.map(scene -> {
					CreatorIdentity creator = creators.getOrDefault(
							scene.getOwnerUserId(),
							unknownCreator());
					return SceneResponse.from(
							scene,
							creator.displayName(),
							creator.handle(),
							creator.avatarGradientStart(),
							creator.avatarGradientEnd(),
							tagsBySceneId.getOrDefault(scene.getId(), List.of()),
							resolveEngagement(scene, currentUserId));
				})
				.toList();
	}

	private SceneEngagementResponse resolveEngagement(Scene scene, Long currentUserId) {
		if (scene.getId() == null) {
			return SceneEngagementResponse.empty();
		}

		SceneEngagementResponse engagement = this.sceneEngagementService.getSceneEngagement(scene.getId(), currentUserId);
		return engagement != null ? engagement : SceneEngagementResponse.empty();
	}

	private Map<Long, CreatorIdentity> resolveCreatorIdentities(List<Scene> scenes) {
		Set<Long> ownerUserIds = scenes.stream()
				.map(Scene::getOwnerUserId)
				.collect(java.util.stream.Collectors.toSet());

		return this.userRepository.findAllById(ownerUserIds).stream()
				.collect(java.util.stream.Collectors.toMap(
						User::getId,
						SceneResponseFactory::creatorIdentity));
	}

	private CreatorIdentity resolveCreatorIdentity(Scene scene) {
		return this.userRepository.findById(scene.getOwnerUserId())
				.map(SceneResponseFactory::creatorIdentity)
				.orElse(unknownCreator());
	}

	private static CreatorIdentity creatorIdentity(User user) {
		return new CreatorIdentity(user.getDisplayName(), user.getHandle(),
				user.getAvatarGradientStart(), user.getAvatarGradientEnd());
	}

	private static CreatorIdentity unknownCreator() {
		return new CreatorIdentity(UNKNOWN_CREATOR_DISPLAY_NAME, null,
				User.DEFAULT_AVATAR_GRADIENT_START, User.DEFAULT_AVATAR_GRADIENT_END);
	}

	private record CreatorIdentity(String displayName, String handle, String avatarGradientStart, String avatarGradientEnd) {
	}

}
