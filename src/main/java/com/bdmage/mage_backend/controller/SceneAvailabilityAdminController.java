package com.bdmage.mage_backend.controller;

import com.bdmage.mage_backend.config.AuthenticatedUserRequest;
import com.bdmage.mage_backend.dto.CustomRenderingControlResponse;
import com.bdmage.mage_backend.dto.SceneControlResponse;
import com.bdmage.mage_backend.dto.UpdateCustomRenderingRequest;
import com.bdmage.mage_backend.dto.UpdateSceneAvailabilityRequest;
import com.bdmage.mage_backend.service.SceneAvailabilityService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class SceneAvailabilityAdminController {

	private final SceneAvailabilityService sceneAvailabilityService;

	public SceneAvailabilityAdminController(SceneAvailabilityService sceneAvailabilityService) {
		this.sceneAvailabilityService = sceneAvailabilityService;
	}

	@GetMapping("/scenes/{id}/availability")
	ResponseEntity<SceneControlResponse> getSceneControl(
			@PathVariable Long id,
			@RequestAttribute(name = AuthenticatedUserRequest.USER_ID_ATTRIBUTE, required = false) Long authenticatedUserId) {
		return ResponseEntity.ok(this.sceneAvailabilityService.sceneControl(id, authenticatedUserId));
	}

	@PutMapping("/scenes/{id}/availability")
	ResponseEntity<SceneControlResponse> setSceneControl(
			@PathVariable Long id,
			@RequestAttribute(name = AuthenticatedUserRequest.USER_ID_ATTRIBUTE, required = false) Long authenticatedUserId,
			@Valid @RequestBody UpdateSceneAvailabilityRequest request) {
		return ResponseEntity.ok(this.sceneAvailabilityService.setSceneControl(
				id, request.disabled(), request.reason(), authenticatedUserId));
	}

	@GetMapping("/rendering/custom")
	ResponseEntity<CustomRenderingControlResponse> getCustomRenderingControl(
			@RequestAttribute(name = AuthenticatedUserRequest.USER_ID_ATTRIBUTE, required = false) Long authenticatedUserId) {
		return ResponseEntity.ok(this.sceneAvailabilityService.customRenderingControl(authenticatedUserId));
	}

	@PutMapping("/rendering/custom")
	ResponseEntity<CustomRenderingControlResponse> setCustomRenderingControl(
			@RequestAttribute(name = AuthenticatedUserRequest.USER_ID_ATTRIBUTE, required = false) Long authenticatedUserId,
			@Valid @RequestBody UpdateCustomRenderingRequest request) {
		return ResponseEntity.ok(this.sceneAvailabilityService.setCustomRenderingControl(
				request.enabled(), request.reason(), authenticatedUserId));
	}
}
