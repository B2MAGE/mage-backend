package com.bdmage.mage_backend.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bdmage.mage_backend.config.AuthenticatedUserRequest;
import com.bdmage.mage_backend.dto.CreatePresetRequest;
import com.bdmage.mage_backend.dto.PresetResponse;
import com.bdmage.mage_backend.model.Preset;
import com.bdmage.mage_backend.service.PresetService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/presets")
public class PresetController {

	private static final ObjectMapper JSON_OBJECT_MAPPER = new ObjectMapper();
	private static final TypeReference<Map<String, Object>> SCENE_DATA_TYPE = new TypeReference<>() {
	};

	private final PresetService presetService;

	public PresetController(PresetService presetService) {
		this.presetService = presetService;
	}

	@PostMapping
	ResponseEntity<PresetResponse> createPreset(
			@RequestAttribute(name = AuthenticatedUserRequest.USER_ID_ATTRIBUTE, required = false) Long authenticatedUserId,
			@Valid @RequestBody CreatePresetRequest request) {
		Preset preset = this.presetService.createPreset(
				authenticatedUserId,
				request.name(),
				JSON_OBJECT_MAPPER.valueToTree(request.sceneData()),
				request.thumbnailRef());

		return ResponseEntity.status(HttpStatus.CREATED)
				.body(new PresetResponse(
						preset.getId(),
						preset.getOwnerUserId(),
						preset.getName(),
						JSON_OBJECT_MAPPER.convertValue(preset.getSceneData(), SCENE_DATA_TYPE),
						preset.getThumbnailRef(),
						preset.getCreatedAt()));
	}

	@GetMapping("/{id}")
	ResponseEntity<PresetResponse> getPreset(@PathVariable Long id) {
		Preset preset = this.presetService.getPreset(id);

		return ResponseEntity.ok(new PresetResponse(
				preset.getId(),
				preset.getOwnerUserId(),
				preset.getName(),
				JSON_OBJECT_MAPPER.convertValue(preset.getSceneData(), SCENE_DATA_TYPE),
				preset.getThumbnailRef(),
				preset.getCreatedAt()));
	}
}
