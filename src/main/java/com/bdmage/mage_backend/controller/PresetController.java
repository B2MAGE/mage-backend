package com.bdmage.mage_backend.controller;

import com.bdmage.mage_backend.config.AuthenticatedUserRequest;
import com.bdmage.mage_backend.dto.CreatePresetRequest;
import com.bdmage.mage_backend.dto.PresetResponse;
import com.bdmage.mage_backend.model.Preset;
import com.bdmage.mage_backend.service.PresetService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/presets")
public class PresetController {

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
				PresetService.sceneDataJson(request.sceneData()),
				request.thumbnailRef());

		return ResponseEntity.status(HttpStatus.CREATED)
				.body(PresetResponse.from(preset));
	}
}
