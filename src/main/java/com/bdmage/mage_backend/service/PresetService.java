package com.bdmage.mage_backend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.bdmage.mage_backend.exception.AuthenticationRequiredException;
import com.bdmage.mage_backend.exception.PresetNotFoundException;
import com.bdmage.mage_backend.model.Preset;
import com.bdmage.mage_backend.repository.PresetRepository;
import com.bdmage.mage_backend.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Service
public class PresetService {

	private static final String AUTHENTICATION_REQUIRED_MESSAGE = "Authentication is required.";

	private final PresetRepository presetRepository;
	private final UserRepository userRepository;

	@PersistenceContext
	private EntityManager entityManager;

	public PresetService(
			PresetRepository presetRepository,
			UserRepository userRepository) {
		this.presetRepository = presetRepository;
		this.userRepository = userRepository;
	}

	@Transactional
	public Preset createPreset(Long authenticatedUserId, String name, JsonNode sceneData, String thumbnailRef) {
		if (authenticatedUserId == null || !this.userRepository.existsById(authenticatedUserId)) {
			throw new AuthenticationRequiredException(AUTHENTICATION_REQUIRED_MESSAGE);
		}

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
			.orElseThrow(() -> new PresetNotFoundException("Preset not found."));
	}

	private static String normalizeThumbnailRef(String thumbnailRef) {
		if (!StringUtils.hasText(thumbnailRef)) {
			return null;
		}

		return thumbnailRef.trim();
	}
}
