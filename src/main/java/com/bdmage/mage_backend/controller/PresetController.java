package com.bdmage.mage_backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bdmage.mage_backend.config.AuthenticatedUserRequest;
import com.bdmage.mage_backend.dto.AttachTagToPresetRequest;
import com.bdmage.mage_backend.dto.CreatePresetRequest;
import com.bdmage.mage_backend.dto.PresetResponse;
import com.bdmage.mage_backend.dto.PresetTagResponse;
import com.bdmage.mage_backend.model.Preset;
import com.bdmage.mage_backend.model.PresetTag;
import com.bdmage.mage_backend.service.PresetService;

import jakarta.validation.Valid;

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

	@PostMapping("/{id}/tags")
	ResponseEntity<PresetTagResponse> attachTagToPreset(
			@RequestAttribute(name = AuthenticatedUserRequest.USER_ID_ATTRIBUTE, required = false) Long authenticatedUserId,
			@PathVariable Long id,
			@Valid @RequestBody AttachTagToPresetRequest request) {
		PresetTag presetTag = this.presetService.attachTagToPreset(authenticatedUserId, id, request.tagId());

		return ResponseEntity.status(HttpStatus.CREATED)
				.body(PresetTagResponse.from(presetTag));
	}

	@GetMapping
	ResponseEntity<List<PresetResponse>> getPresets(@RequestParam(required = false) String tag) {
		List<Preset> presets = StringUtils.hasText(tag)
				? this.presetService.getPresetsByTag(tag)
				: this.presetService.getAllPresets();

		return ResponseEntity.ok(presets.stream()
				.map(PresetResponse::from)
				.toList());
	}

	@GetMapping("/{id}")
	ResponseEntity<PresetResponse> getPreset(@PathVariable Long id) {
		Preset preset = this.presetService.getPreset(id);

		return ResponseEntity.ok(PresetResponse.from(preset));
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> deletePreset(
        	@RequestAttribute(name = AuthenticatedUserRequest.USER_ID_ATTRIBUTE, required = false) Long authenticatedUserId,
        	@PathVariable Long id) {
    	this.presetService.deletePreset(authenticatedUserId, id);
    	return ResponseEntity.noContent().build();
	}
}
