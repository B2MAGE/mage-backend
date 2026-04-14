package com.bdmage.mage_backend.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.bdmage.mage_backend.config.ThumbnailStorageProperties;
import com.bdmage.mage_backend.exception.AuthenticationRequiredException;
import com.bdmage.mage_backend.exception.InvalidThumbnailException;
import com.bdmage.mage_backend.exception.PresetForbiddenException;
import com.bdmage.mage_backend.exception.PresetNotFoundException;
import com.bdmage.mage_backend.exception.PresetOwnershipRequiredException;
import com.bdmage.mage_backend.exception.PresetTagAlreadyExistsException;
import com.bdmage.mage_backend.exception.TagNotFoundException;
import com.bdmage.mage_backend.model.Preset;
import com.bdmage.mage_backend.model.PresetTag;
import com.bdmage.mage_backend.model.Tag;
import com.bdmage.mage_backend.repository.PresetRepository;
import com.bdmage.mage_backend.repository.PresetTagRepository;
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
public class PresetService {

	private static final String AUTHENTICATION_REQUIRED_MESSAGE = "Authentication is required.";
	private static final String PRESET_FORBIDDEN_MESSAGE = "You do not have permission to delete this preset.";
	private static final String PRESET_NOT_FOUND_MESSAGE = "Preset not found.";
	private static final String TAG_NOT_FOUND_MESSAGE = "Tag not found.";
	private static final String PRESET_TAG_ALREADY_EXISTS_MESSAGE = "This tag is already attached to the preset.";
	private static final String PRESET_OWNERSHIP_REQUIRED_MESSAGE = "Preset ownership is required.";
	private static final String INVALID_THUMBNAIL_EMPTY_MESSAGE = "Thumbnail file must not be empty.";
	private static final String INVALID_THUMBNAIL_FILENAME_MESSAGE = "Thumbnail filename must not be blank.";
	private static final String INVALID_THUMBNAIL_TYPE_MESSAGE = "Thumbnail must be a valid image (jpeg, png, webp, or gif).";
	private static final String INVALID_THUMBNAIL_SIZE_MESSAGE = "Thumbnail must not exceed 5 MB.";
	private static final ObjectMapper JSON_OBJECT_MAPPER = new ObjectMapper();

	private final PresetRepository presetRepository;
	private final TagRepository tagRepository;
	private final PresetTagRepository presetTagRepository;
	private final UserRepository userRepository;
	private final ThumbnailStorageService thumbnailStorageService;
	private final ThumbnailStorageProperties thumbnailStorageProperties;

	@PersistenceContext
	private EntityManager entityManager;

	// Keep the four-argument constructor for tests that do not need storage.
	public PresetService(
			PresetRepository presetRepository,
			TagRepository tagRepository,
			PresetTagRepository presetTagRepository,
			UserRepository userRepository) {
		this(presetRepository, tagRepository, presetTagRepository, userRepository, null, null);
	}

	// Spring uses this constructor in production because it can satisfy all dependencies.
	@Autowired
	public PresetService(
			PresetRepository presetRepository,
			TagRepository tagRepository,
			PresetTagRepository presetTagRepository,
			UserRepository userRepository,
			ThumbnailStorageService thumbnailStorageService,
			ThumbnailStorageProperties thumbnailStorageProperties) {
		this.presetRepository = presetRepository;
		this.tagRepository = tagRepository;
		this.presetTagRepository = presetTagRepository;
		this.userRepository = userRepository;
		this.thumbnailStorageService = thumbnailStorageService;
		this.thumbnailStorageProperties = thumbnailStorageProperties;
	}

	@Transactional
	public Preset createPreset(Long authenticatedUserId, String name, JsonNode sceneData, String thumbnailObjectKey) {
		requireAuthenticatedUser(authenticatedUserId);

		ThumbnailStorageService.FinalizedThumbnail finalizedThumbnail = null;
		if (StringUtils.hasText(thumbnailObjectKey)) {
			finalizedThumbnail = requireThumbnailStorageService()
					.finalizePresetCreationUpload(authenticatedUserId, thumbnailObjectKey);
		}

		Preset newPreset = new Preset(
				authenticatedUserId,
				name.trim(),
				sceneData,
				finalizedThumbnail != null ? finalizedThumbnail.publicUrl() : null);

		try {
			Preset savedPreset = this.presetRepository.saveAndFlush(newPreset);
			if (this.entityManager != null) {
				this.entityManager.refresh(savedPreset);
			}
			return savedPreset;
		} catch (RuntimeException ex) {
			if (finalizedThumbnail != null) {
				requireThumbnailStorageService().delete(finalizedThumbnail.objectKey());
			}
			throw ex;
		}
	}

	@Transactional
	public void deletePreset(Long authenticatedUserId, Long presetId) {
		requireAuthenticatedUser(authenticatedUserId);

		Preset preset = this.presetRepository.findById(presetId)
				.orElseThrow(() -> new PresetNotFoundException(PRESET_NOT_FOUND_MESSAGE));
		if (!preset.getOwnerUserId().equals(authenticatedUserId)) {
			throw new PresetForbiddenException(PRESET_FORBIDDEN_MESSAGE);
		}

		this.presetRepository.delete(preset);
	}

	@Transactional(readOnly = true)
	public Preset getPreset(Long presetId) {
		return this.presetRepository.findById(presetId)
				.orElseThrow(() -> new PresetNotFoundException(PRESET_NOT_FOUND_MESSAGE));
	}

	@Transactional(readOnly = true)
	public List<Preset> getPresetsForUser(Long authenticatedUserId, Long requestedUserId) {
		requireAuthenticatedUser(authenticatedUserId);
		return this.presetRepository.findAllByOwnerUserId(requestedUserId);
	}

	@Transactional(readOnly = true)
	public List<Preset> getAllPresets() {
		return this.presetRepository.findAll();
	}

	@Transactional(readOnly = true)
	public List<Preset> getPresetsByTag(String tag) {
		return this.presetRepository.findAllByTagName(normalizeTagName(tag));
	}

	@Transactional(readOnly = true)
	public List<String> getTagNamesForPreset(Long presetId) {
		List<Long> tagIds = this.presetTagRepository.findAllByPresetId(presetId).stream()
				.map(PresetTag::getTagId)
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
	public PresetTag attachTagToPreset(Long authenticatedUserId, Long presetId, Long tagId) {
		requireAuthenticatedUser(authenticatedUserId);
		requirePresetExists(presetId);
		requireTagExists(tagId);

		if (this.presetTagRepository.existsByPresetIdAndTagId(presetId, tagId)) {
			throw new PresetTagAlreadyExistsException(PRESET_TAG_ALREADY_EXISTS_MESSAGE);
		}

		try {
			return this.presetTagRepository.saveAndFlush(new PresetTag(presetId, tagId));
		} catch (DataIntegrityViolationException ex) {
			requirePresetExists(presetId);
			requireTagExists(tagId);
			throw new PresetTagAlreadyExistsException(PRESET_TAG_ALREADY_EXISTS_MESSAGE);
		}
	}

	@Transactional
	public ThumbnailStorageService.PresignedThumbnailUpload createPresetThumbnailUpload(
			Long authenticatedUserId,
			String filename,
			String contentType,
			Long sizeBytes) {
		requireAuthenticatedUser(authenticatedUserId);
		validateThumbnailUploadRequest(filename, contentType, sizeBytes);
		return requireThumbnailStorageService().createPresetCreationUpload(authenticatedUserId, filename, contentType);
	}

	@Transactional
	public ThumbnailStorageService.PresignedThumbnailUpload createThumbnailUpload(
			Long authenticatedUserId,
			Long presetId,
			String filename,
			String contentType,
			Long sizeBytes) {
		requireAuthenticatedUser(authenticatedUserId);

		Preset preset = this.presetRepository.findById(presetId)
				.orElseThrow(() -> new PresetNotFoundException(PRESET_NOT_FOUND_MESSAGE));

		requirePresetOwnership(preset, authenticatedUserId);
		validateThumbnailUploadRequest(filename, contentType, sizeBytes);

		return requireThumbnailStorageService().createPresignedUpload(presetId, filename, contentType);
	}

	@Transactional
	public Preset finalizeThumbnailUpload(Long authenticatedUserId, Long presetId, String objectKey) {
		requireAuthenticatedUser(authenticatedUserId);

		Preset preset = this.presetRepository.findById(presetId)
				.orElseThrow(() -> new PresetNotFoundException(PRESET_NOT_FOUND_MESSAGE));

		requirePresetOwnership(preset, authenticatedUserId);

		String previousThumbnailRef = preset.getThumbnailRef();
		ThumbnailStorageService.FinalizedThumbnail finalizedThumbnail = requireThumbnailStorageService()
				.finalizeUpload(presetId, objectKey);
		preset.updateThumbnailRef(finalizedThumbnail.publicUrl());

		Preset savedPreset = this.presetRepository.saveAndFlush(preset);
		if (this.entityManager != null) {
			this.entityManager.refresh(savedPreset);
		}
		if (StringUtils.hasText(previousThumbnailRef) && !previousThumbnailRef.equals(finalizedThumbnail.publicUrl())) {
			requireThumbnailStorageService().delete(previousThumbnailRef);
		}
		return savedPreset;
	}

	public static JsonNode sceneDataJson(Map<String, Object> sceneData) {
		return JSON_OBJECT_MAPPER.valueToTree(sceneData);
	}

	private void requireAuthenticatedUser(Long authenticatedUserId) {
		if (authenticatedUserId == null || !this.userRepository.existsById(authenticatedUserId)) {
			throw new AuthenticationRequiredException(AUTHENTICATION_REQUIRED_MESSAGE);
		}
	}

	private void requirePresetExists(Long presetId) {
		if (!this.presetRepository.existsById(presetId)) {
			throw new PresetNotFoundException(PRESET_NOT_FOUND_MESSAGE);
		}
	}

	private void requireTagExists(Long tagId) {
		if (!this.tagRepository.existsById(tagId)) {
			throw new TagNotFoundException(TAG_NOT_FOUND_MESSAGE);
		}
	}

	private static String normalizeTagName(String tag) {
		return tag.trim().toLowerCase(Locale.ROOT);
	}

	private void requirePresetOwnership(Preset preset, Long authenticatedUserId) {
		if (!preset.getOwnerUserId().equals(authenticatedUserId)) {
			throw new PresetOwnershipRequiredException(PRESET_OWNERSHIP_REQUIRED_MESSAGE);
		}
	}

	private ThumbnailStorageService requireThumbnailStorageService() {
		if (this.thumbnailStorageService == null) {
			throw new IllegalStateException("Thumbnail storage service is not configured.");
		}

		return this.thumbnailStorageService;
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
