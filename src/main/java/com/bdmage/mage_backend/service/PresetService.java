package com.bdmage.mage_backend.service;

import java.util.List;
import java.util.Map;

import com.bdmage.mage_backend.exception.AuthenticationRequiredException;
import com.bdmage.mage_backend.exception.PresetNotFoundException;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class PresetService {

	private static final String AUTHENTICATION_REQUIRED_MESSAGE = "Authentication is required.";
	private static final String PRESET_NOT_FOUND_MESSAGE = "Preset not found.";
	private static final String TAG_NOT_FOUND_MESSAGE = "Tag not found.";
	private static final String PRESET_TAG_ALREADY_EXISTS_MESSAGE = "This tag is already attached to the preset.";
	private static final ObjectMapper JSON_OBJECT_MAPPER = new ObjectMapper();

	private final PresetRepository presetRepository;
	private final TagRepository tagRepository;
	private final PresetTagRepository presetTagRepository;
	private final UserRepository userRepository;

	@PersistenceContext
	private EntityManager entityManager;

	public PresetService(
			PresetRepository presetRepository,
			TagRepository tagRepository,
			PresetTagRepository presetTagRepository,
			UserRepository userRepository) {
		this.presetRepository = presetRepository;
		this.tagRepository = tagRepository;
		this.presetTagRepository = presetTagRepository;
		this.userRepository = userRepository;
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
}
