package com.bdmage.mage_backend.controller;

import java.util.List;

import com.bdmage.mage_backend.config.AuthenticatedUserRequest;
import com.bdmage.mage_backend.dto.CustomRenderingAvailabilityResponse;
import com.bdmage.mage_backend.dto.SceneAvailabilityResponse;
import com.bdmage.mage_backend.dto.SceneRepairResponse;
import com.bdmage.mage_backend.model.Scene;
import com.bdmage.mage_backend.service.SceneAvailabilityService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SceneAvailabilityController {

	private final SceneAvailabilityService sceneAvailabilityService;

	public SceneAvailabilityController(SceneAvailabilityService sceneAvailabilityService) {
		this.sceneAvailabilityService = sceneAvailabilityService;
	}

	@GetMapping("/scene-availability")
	ResponseEntity<List<SceneAvailabilityResponse>> getAvailability(
			@RequestParam(required = false) List<Long> ids) {
		return ResponseEntity.ok(this.sceneAvailabilityService.statusesByIds(ids));
	}

	@GetMapping("/scene-availability/{id}")
	ResponseEntity<SceneAvailabilityResponse> getAvailability(@PathVariable Long id) {
		return ResponseEntity.ok(this.sceneAvailabilityService.status(id));
	}

	@GetMapping("/rendering-status")
	ResponseEntity<CustomRenderingAvailabilityResponse> getRenderingStatus() {
		return ResponseEntity.ok(this.sceneAvailabilityService.customRenderingStatus());
	}

	@GetMapping("/scenes/{id}/repair")
	ResponseEntity<SceneRepairResponse> getSceneForRepair(
			@PathVariable Long id,
			@RequestAttribute(name = AuthenticatedUserRequest.USER_ID_ATTRIBUTE, required = false) Long authenticatedUserId) {
		Scene scene = this.sceneAvailabilityService.repairScene(id, authenticatedUserId);
		return ResponseEntity.ok(SceneRepairResponse.from(scene, this.sceneAvailabilityService.status(scene)));
	}
}
