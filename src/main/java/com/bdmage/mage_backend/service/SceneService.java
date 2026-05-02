package com.bdmage.mage_backend.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.bdmage.mage_backend.config.ThumbnailStorageProperties;
import com.bdmage.mage_backend.exception.AuthenticationRequiredException;
import com.bdmage.mage_backend.exception.InvalidThumbnailException;
import com.bdmage.mage_backend.exception.SceneForbiddenException;
import com.bdmage.mage_backend.exception.SceneNotFoundException;
import com.bdmage.mage_backend.exception.SceneOwnershipRequiredException;
import com.bdmage.mage_backend.exception.SceneTagAlreadyExistsException;
import com.bdmage.mage_backend.exception.TagNotFoundException;
import com.bdmage.mage_backend.model.Scene;
import com.bdmage.mage_backend.model.SceneTag;
import com.bdmage.mage_backend.model.SceneTagId;
import com.bdmage.mage_backend.model.Tag;
import com.bdmage.mage_backend.repository.SceneRepository;
import com.bdmage.mage_backend.repository.SceneTagRepository;
import com.bdmage.mage_backend.repository.TagRepository;
import com.bdmage.mage_backend.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SceneService {

	private static final String AUTHENTICATION_REQUIRED_MESSAGE = "Authentication is required.";
	private static final String SCENE_FORBIDDEN_MESSAGE = "You do not have permission to delete this scene.";
	private static final String SCENE_NOT_FOUND_MESSAGE = "Scene not found.";
	private static final String TAG_NOT_FOUND_MESSAGE = "Tag not found.";
	private static final String SCENE_TAG_ALREADY_EXISTS_MESSAGE = "This tag is already attached to the scene.";
	private static final String SCENE_OWNERSHIP_REQUIRED_MESSAGE = "Scene ownership is required.";
	private static final String INVALID_THUMBNAIL_EMPTY_MESSAGE = "Thumbnail file must not be empty.";
	private static final String INVALID_THUMBNAIL_FILENAME_MESSAGE = "Thumbnail filename must not be blank.";
	private static final String INVALID_THUMBNAIL_TYPE_MESSAGE = "Thumbnail must be a valid image (jpeg, png, webp, or gif).";
	private static final String INVALID_THUMBNAIL_SIZE_MESSAGE = "Thumbnail must not exceed 5 MB.";
	private static final ObjectMapper JSON_OBJECT_MAPPER = new ObjectMapper();

	private final SceneRepository sceneRepository;
	private final TagRepository tagRepository;
	private final SceneTagRepository sceneTagRepository;
	private final UserRepository userRepository;
	private final ThumbnailStorageService thumbnailStorageService;
	private final ThumbnailStorageProperties thumbnailStorageProperties;
	private final PlaylistService playlistService;

	@PersistenceContext
	private EntityManager entityManager;

	// Keep the four-argument constructor for tests that do not need storage.
	public SceneService(
			SceneRepository sceneRepository,
			TagRepository tagRepository,
			SceneTagRepository sceneTagRepository,
			UserRepository userRepository) {
		this(sceneRepository, tagRepository, sceneTagRepository, userRepository, null, null, null);
	}

	// Keep this constructor for tests that need storage.
	public SceneService(
			SceneRepository sceneRepository,
			TagRepository tagRepository,
			SceneTagRepository sceneTagRepository,
			UserRepository userRepository,
			ThumbnailStorageService thumbnailStorageService,
			ThumbnailStorageProperties thumbnailStorageProperties) {
		this(sceneRepository, tagRepository, sceneTagRepository, userRepository,
				thumbnailStorageService, thumbnailStorageProperties, null);
	}

	// Spring uses this constructor in production.
	@Autowired
	public SceneService(
			SceneRepository sceneRepository,
			TagRepository tagRepository,
			SceneTagRepository sceneTagRepository,
			UserRepository userRepository,
			ThumbnailStorageService thumbnailStorageService,
			ThumbnailStorageProperties thumbnailStorageProperties,
			PlaylistService playlistService) {
		this.sceneRepository = sceneRepository;
		this.tagRepository = tagRepository;
		this.sceneTagRepository = sceneTagRepository;
		this.userRepository = userRepository;
		this.thumbnailStorageService = thumbnailStorageService;
		this.thumbnailStorageProperties = thumbnailStorageProperties;
		this.playlistService = playlistService;
	}

	@Transactional
	public Scene createScene(
			Long authenticatedUserId,
			String name,
			String description,
			JsonNode sceneData,
			String thumbnailObjectKey) {
		return createScene(authenticatedUserId, name, description, sceneData, thumbnailObjectKey, null);
	}

	@Transactional
	public Scene createScene(
			Long authenticatedUserId,
			String name,
			String description,
			JsonNode sceneData,
			String thumbnailObjectKey,
			Long playlistId) {
		requireAuthenticatedUser(authenticatedUserId);

		ThumbnailStorageService.FinalizedThumbnail finalizedThumbnail = null;
		if (StringUtils.hasText(thumbnailObjectKey)) {
			finalizedThumbnail = requireThumbnailStorageService()
					.finalizeSceneCreationUpload(authenticatedUserId, thumbnailObjectKey);
		}

		Scene newScene = new Scene(
				authenticatedUserId,
				name.trim(),
				normalizeOptionalText(description),
				sceneData,
				finalizedThumbnail != null ? finalizedThumbnail.publicUrl() : null);

		try {
			Scene savedScene = this.sceneRepository.saveAndFlush(newScene);
			if (this.entityManager != null) {
				this.entityManager.refresh(savedScene);
			}
			if (playlistId != null) {
				requirePlaylistService().attachSceneToPlaylist(authenticatedUserId, savedScene, playlistId);
			}
			return savedScene;
		} catch (RuntimeException ex) {
			if (finalizedThumbnail != null) {
				requireThumbnailStorageService().delete(finalizedThumbnail.objectKey());
			}
			throw ex;
		}
	}

	@Transactional
	public void deleteScene(Long authenticatedUserId, Long sceneId) {
		requireAuthenticatedUser(authenticatedUserId);

		Scene scene = this.sceneRepository.findById(sceneId)
				.orElseThrow(() -> new SceneNotFoundException(SCENE_NOT_FOUND_MESSAGE));
		if (!scene.getOwnerUserId().equals(authenticatedUserId)) {
			throw new SceneForbiddenException(SCENE_FORBIDDEN_MESSAGE);
		}

		this.sceneRepository.delete(scene);
	}

	@Transactional(readOnly = true)
	public Scene getScene(Long sceneId) {
		return this.sceneRepository.findById(sceneId)
				.orElseThrow(() -> new SceneNotFoundException(SCENE_NOT_FOUND_MESSAGE));
	}

	@Transactional(readOnly = true)
	public List<Scene> getScenesForUser(Long authenticatedUserId, Long requestedUserId) {
		requireAuthenticatedUser(authenticatedUserId);
		return this.sceneRepository.findAllByOwnerUserId(requestedUserId);
	}

	@Transactional(readOnly = true)
	public List<Scene> getAllScenes() {
		return this.sceneRepository.findAll();
	}

	@Transactional(readOnly = true)
	public List<Scene> getScenesByTag(String tag) {
		return this.sceneRepository.findAllByTagName(normalizeTagName(tag));
	}

	@Transactional(readOnly = true)
	public List<String> getTagNamesForScene(Long sceneId) {
		List<Long> tagIds = this.sceneTagRepository.findAllBySceneId(sceneId).stream()
				.map(SceneTag::getTagId)
				.distinct()
				.toList();

		if (tagIds.isEmpty()) {
			return List.of();
		}

		return this.tagRepository.findAllById(tagIds).stream()
				.map(Tag::getName)
				.sorted()
				.toList();
	}

	@Transactional
	public SceneTag attachTagToScene(Long authenticatedUserId, Long sceneId, Long tagId) {
		requireOwnedScene(authenticatedUserId, sceneId);
		requireTagExists(tagId);

		if (this.sceneTagRepository.existsBySceneIdAndTagId(sceneId, tagId)) {
			throw new SceneTagAlreadyExistsException(SCENE_TAG_ALREADY_EXISTS_MESSAGE);
		}

		try {
			return this.sceneTagRepository.saveAndFlush(new SceneTag(sceneId, tagId));
		} catch (DataIntegrityViolationException ex) {
			requireSceneExists(sceneId);
			requireTagExists(tagId);
			throw new SceneTagAlreadyExistsException(SCENE_TAG_ALREADY_EXISTS_MESSAGE);
		}
	}

	@Transactional
	public List<SceneTag> replaceSceneTags(Long authenticatedUserId, Long sceneId, List<Long> tagIds) {
		requireOwnedScene(authenticatedUserId, sceneId);
		List<Long> uniqueTagIds = normalizeTagIds(tagIds);

		for (Long tagId : uniqueTagIds) {
			requireTagExists(tagId);
		}

		List<SceneTag> existingSceneTags = this.sceneTagRepository.findAllBySceneId(sceneId);
		if (!existingSceneTags.isEmpty()) {
			this.sceneTagRepository.deleteAll(existingSceneTags);
			this.sceneTagRepository.flush();
		}

		if (uniqueTagIds.isEmpty()) {
			return List.of();
		}

		return this.sceneTagRepository.saveAllAndFlush(uniqueTagIds.stream()
				.map(tagId -> new SceneTag(sceneId, tagId))
				.toList());
	}

	@Transactional
	public void removeTagFromScene(Long authenticatedUserId, Long sceneId, Long tagId) {
		requireOwnedScene(authenticatedUserId, sceneId);
		requireTagExists(tagId);

		SceneTagId sceneTagId = new SceneTagId(sceneId, tagId);
		if (this.sceneTagRepository.existsById(sceneTagId)) {
			this.sceneTagRepository.deleteById(sceneTagId);
			this.sceneTagRepository.flush();
		}
	}

	@Transactional
	public ThumbnailStorageService.PresignedThumbnailUpload createSceneThumbnailUpload(
			Long authenticatedUserId,
			String filename,
			String contentType,
			Long sizeBytes) {
		requireAuthenticatedUser(authenticatedUserId);
		validateThumbnailUploadRequest(filename, contentType, sizeBytes);
		return requireThumbnailStorageService().createSceneCreationUpload(authenticatedUserId, filename, contentType);
	}

	@Transactional
	public ThumbnailStorageService.PresignedThumbnailUpload createThumbnailUpload(
			Long authenticatedUserId,
			Long sceneId,
			String filename,
			String contentType,
			Long sizeBytes) {
		requireAuthenticatedUser(authenticatedUserId);

		Scene scene = this.sceneRepository.findById(sceneId)
				.orElseThrow(() -> new SceneNotFoundException(SCENE_NOT_FOUND_MESSAGE));

		requireSceneOwnership(scene, authenticatedUserId);
		validateThumbnailUploadRequest(filename, contentType, sizeBytes);

		return requireThumbnailStorageService().createPresignedUpload(sceneId, filename, contentType);
	}

	@Transactional
	public Scene finalizeThumbnailUpload(Long authenticatedUserId, Long sceneId, String objectKey) {
		requireAuthenticatedUser(authenticatedUserId);

		Scene scene = this.sceneRepository.findById(sceneId)
				.orElseThrow(() -> new SceneNotFoundException(SCENE_NOT_FOUND_MESSAGE));

		requireSceneOwnership(scene, authenticatedUserId);

		String previousThumbnailRef = scene.getThumbnailRef();
		ThumbnailStorageService.FinalizedThumbnail finalizedThumbnail = requireThumbnailStorageService()
				.finalizeUpload(sceneId, objectKey);
		scene.updateThumbnailRef(finalizedThumbnail.publicUrl());

		Scene savedScene = this.sceneRepository.saveAndFlush(scene);
		if (this.entityManager != null) {
			this.entityManager.refresh(savedScene);
		}
		if (StringUtils.hasText(previousThumbnailRef) && !previousThumbnailRef.equals(finalizedThumbnail.publicUrl())) {
			requireThumbnailStorageService().delete(previousThumbnailRef);
		}
		return savedScene;
	}

	@Transactional
	public Scene updateDescription(Long authenticatedUserId, Long sceneId, String description) {
		Scene scene = requireOwnedScene(authenticatedUserId, sceneId);
		scene.updateDescription(normalizeOptionalText(description));

		Scene savedScene = this.sceneRepository.saveAndFlush(scene);
		if (this.entityManager != null) {
			this.entityManager.refresh(savedScene);
		}
		return savedScene;
	}

	@Transactional
	public Scene updateScene(Long authenticatedUserId, Long sceneId, String name, String description, JsonNode sceneData) {
		Scene scene = requireOwnedScene(authenticatedUserId, sceneId);
		scene.updateDetails(name.trim(), normalizeOptionalText(description), sceneData);

		Scene savedScene = this.sceneRepository.saveAndFlush(scene);
		if (this.entityManager != null) {
			this.entityManager.refresh(savedScene);
		}
		return savedScene;
	}

	public static JsonNode sceneDataJson(Map<String, Object> sceneData) {
		return JSON_OBJECT_MAPPER.valueToTree(sceneData);
	}

	private void requireAuthenticatedUser(Long authenticatedUserId) {
		if (authenticatedUserId == null || !this.userRepository.existsById(authenticatedUserId)) {
			throw new AuthenticationRequiredException(AUTHENTICATION_REQUIRED_MESSAGE);
		}
	}

	private void requireSceneExists(Long sceneId) {
		if (!this.sceneRepository.existsById(sceneId)) {
			throw new SceneNotFoundException(SCENE_NOT_FOUND_MESSAGE);
		}
	}

	private void requireTagExists(Long tagId) {
		if (!this.tagRepository.existsById(tagId)) {
			throw new TagNotFoundException(TAG_NOT_FOUND_MESSAGE);
		}
	}

	private Scene requireOwnedScene(Long authenticatedUserId, Long sceneId) {
		requireAuthenticatedUser(authenticatedUserId);

		Scene scene = this.sceneRepository.findById(sceneId)
				.orElseThrow(() -> new SceneNotFoundException(SCENE_NOT_FOUND_MESSAGE));

		requireSceneOwnership(scene, authenticatedUserId);
		return scene;
	}

	private List<Long> normalizeTagIds(List<Long> tagIds) {
		if (tagIds == null) {
			return List.of();
		}

		LinkedHashSet<Long> uniqueTagIds = new LinkedHashSet<>();
		for (Long tagId : tagIds) {
			if (tagId == null) {
				throw new TagNotFoundException(TAG_NOT_FOUND_MESSAGE);
			}
			uniqueTagIds.add(tagId);
		}

		return List.copyOf(uniqueTagIds);
	}

	private static String normalizeTagName(String tag) {
		return tag.trim().toLowerCase(Locale.ROOT);
	}

	private static String normalizeOptionalText(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}

	private void requireSceneOwnership(Scene scene, Long authenticatedUserId) {
		if (!scene.getOwnerUserId().equals(authenticatedUserId)) {
			throw new SceneOwnershipRequiredException(SCENE_OWNERSHIP_REQUIRED_MESSAGE);
		}
	}

	private ThumbnailStorageService requireThumbnailStorageService() {
		if (this.thumbnailStorageService == null) {
			throw new IllegalStateException("Thumbnail storage service is not configured.");
		}

		return this.thumbnailStorageService;
	}

	private PlaylistService requirePlaylistService() {
		if (this.playlistService == null) {
			throw new IllegalStateException("Playlist service is not configured.");
		}

		return this.playlistService;
	}

	private ThumbnailStorageProperties requireThumbnailStorageProperties() {
		if (this.thumbnailStorageProperties == null) {
			throw new IllegalStateException("Thumbnail storage properties are not configured.");
		}

		return this.thumbnailStorageProperties;
	}

	private void validateThumbnailUploadRequest(String filename, String contentType, Long sizeBytes) {
		if (!StringUtils.hasText(filename)) {
			throw new InvalidThumbnailException(INVALID_THUMBNAIL_FILENAME_MESSAGE);
		}
		if (sizeBytes == null || sizeBytes <= 0L) {
			throw new InvalidThumbnailException(INVALID_THUMBNAIL_EMPTY_MESSAGE);
		}
		if (!requireThumbnailStorageProperties().allowedContentTypeSet().contains(contentType)) {
			throw new InvalidThumbnailException(INVALID_THUMBNAIL_TYPE_MESSAGE);
		}
		if (sizeBytes > requireThumbnailStorageProperties().maxBytes()) {
			throw new InvalidThumbnailException(INVALID_THUMBNAIL_SIZE_MESSAGE);
		}
	}
}
