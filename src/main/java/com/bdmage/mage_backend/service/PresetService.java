package com.bdmage.mage_backend.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.bdmage.mage_backend.exception.AuthenticationRequiredException;
import com.bdmage.mage_backend.exception.InvalidThumbnailException;
import com.bdmage.mage_backend.exception.PresetNotFoundException;
import com.bdmage.mage_backend.exception.PresetOwnershipRequiredException;
import com.bdmage.mage_backend.exception.PresetTagAlreadyExistsException;
import com.bdmage.mage_backend.exception.TagNotFoundException;
import com.bdmage.mage_backend.model.Preset;
import com.bdmage.mage_backend.model.PresetTag;
import com.bdmage.mage_backend.repository.PresetTagRepository;
import com.bdmage.mage_backend.repository.PresetRepository;
import com.bdmage.mage_backend.repository.TagRepository;
import com.bdmage.mage_backend.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PresetService {

	private static final String AUTHENTICATION_REQUIRED_MESSAGE = "Authentication is required.";
	private static final String PRESET_NOT_FOUND_MESSAGE = "Preset not found.";
	private static final String TAG_NOT_FOUND_MESSAGE = "Tag not found.";
	private static final String PRESET_TAG_ALREADY_EXISTS_MESSAGE = "This tag is already attached to the preset.";
	private static final String PRESET_OWNERSHIP_REQUIRED_MESSAGE = "Preset ownership is required.";
	private static final String INVALID_THUMBNAIL_EMPTY_MESSAGE = "Thumbnail file must not be empty.";
	private static final String INVALID_THUMBNAIL_TYPE_MESSAGE = "Thumbnail must be a valid image (jpeg, png, webp, or gif).";
	private static final String INVALID_THUMBNAIL_SIZE_MESSAGE = "Thumbnail must not exceed 5 MB.";
	private static final long MAX_THUMBNAIL_SIZE_BYTES = 5L * 1024 * 1024;
	private static final Set<String> ALLOWED_THUMBNAIL_CONTENT_TYPES = Set.of(
			"image/jpeg", "image/png", "image/webp", "image/gif");
	private static final ObjectMapper JSON_OBJECT_MAPPER = new ObjectMapper();

	private final PresetRepository presetRepository;
	private final TagRepository tagRepository;
	private final PresetTagRepository presetTagRepository;
	private final UserRepository userRepository;
	private final ThumbnailStorageService thumbnailStorageService;

	@PersistenceContext
	private EntityManager entityManager;

	// This is the original constructor — kept so existing tests don't need to change
	public PresetService(
			PresetRepository presetRepository,
			TagRepository tagRepository,
			PresetTagRepository presetTagRepository,
			UserRepository userRepository) {
		this(presetRepository, tagRepository, presetTagRepository, userRepository, null);
	}

	// Spring uses this one in production since it can satisfy all five dependencies
	@Autowired
	public PresetService(
			PresetRepository presetRepository,
			TagRepository tagRepository,
			PresetTagRepository presetTagRepository,
			UserRepository userRepository,
			ThumbnailStorageService thumbnailStorageService) {
		this.presetRepository = presetRepository;
		this.tagRepository = tagRepository;
		this.presetTagRepository = presetTagRepository;
		this.userRepository = userRepository;
		this.thumbnailStorageService = thumbnailStorageService;
	}

	@Transactional
	public Preset createPreset(Long authenticatedUserId, String name, JsonNode sceneData, String thumbnailRef) {
		requireAuthenticatedUser(authenticatedUserId);

		Preset newPreset = new Preset(
				authenticatedUserId,
				name.trim(),
				sceneData,
				normalizeThumbnailRef(thumbnailRef));
		Preset savedPreset = this.presetRepository.saveAndFlush(newPreset);
		if (this.entityManager != null) {
			this.entityManager.refresh(savedPreset);
		}
		return savedPreset;
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
	public Preset uploadThumbnail(Long authenticatedUserId, Long presetId, MultipartFile file) {
		requireAuthenticatedUser(authenticatedUserId);

		Preset preset = this.presetRepository.findById(presetId)
				.orElseThrow(() -> new PresetNotFoundException(PRESET_NOT_FOUND_MESSAGE));

		requirePresetOwnership(preset, authenticatedUserId);
		validateThumbnail(file);

		String thumbnailRef = this.thumbnailStorageService.store(file, presetId);
		preset.updateThumbnailRef(thumbnailRef);

		Preset savedPreset = this.presetRepository.saveAndFlush(preset);
		if (this.entityManager != null) {
			this.entityManager.refresh(savedPreset);
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

	private static String normalizeThumbnailRef(String thumbnailRef) {
		if (!StringUtils.hasText(thumbnailRef)) {
			return null;
		}

		return thumbnailRef.trim();
	}

	private static String normalizeTagName(String tag) {
		return tag.trim().toLowerCase(Locale.ROOT);
	}

	private void requirePresetOwnership(Preset preset, Long authenticatedUserId) {
		if (!preset.getOwnerUserId().equals(authenticatedUserId)) {
			throw new PresetOwnershipRequiredException(PRESET_OWNERSHIP_REQUIRED_MESSAGE);
		}
	}

	private static void validateThumbnail(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new InvalidThumbnailException(INVALID_THUMBNAIL_EMPTY_MESSAGE);
		}
		String contentType = file.getContentType();
		if (contentType == null || !ALLOWED_THUMBNAIL_CONTENT_TYPES.contains(contentType)) {
			throw new InvalidThumbnailException(INVALID_THUMBNAIL_TYPE_MESSAGE);
		}
		if (file.getSize() > MAX_THUMBNAIL_SIZE_BYTES) {
			throw new InvalidThumbnailException(INVALID_THUMBNAIL_SIZE_MESSAGE);
		}
	}
}
