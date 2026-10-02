package com.bdmage.mage_backend.controller;

import java.util.List;

import com.bdmage.mage_backend.config.AuthenticatedUserRequest;
import com.bdmage.mage_backend.dto.PublicProfileResponse;
import com.bdmage.mage_backend.dto.SceneResponse;
import com.bdmage.mage_backend.model.Scene;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.service.SceneResponseFactory;
import com.bdmage.mage_backend.service.SceneService;
import com.bdmage.mage_backend.service.UserProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profiles")
public class ProfileController {

	private final SceneResponseFactory sceneResponseFactory;
	private final SceneService sceneService;
	private final UserProfileService userProfileService;

	public ProfileController(
			SceneResponseFactory sceneResponseFactory,
			SceneService sceneService,
			UserProfileService userProfileService) {
		this.sceneResponseFactory = sceneResponseFactory;
		this.sceneService = sceneService;
		this.userProfileService = userProfileService;
	}

	@GetMapping("/{handle}")
	ResponseEntity<PublicProfileResponse> profile(
			@PathVariable String handle,
			@RequestAttribute(name = AuthenticatedUserRequest.USER_ID_ATTRIBUTE, required = false) Long authenticatedUserId) {
		User user = this.userProfileService.getPublicProfile(handle);
		List<Scene> scenes = this.sceneService.getPublicScenesForUser(user.getId());
		List<SceneResponse> sceneResponses = this.sceneResponseFactory.from(scenes, authenticatedUserId);

		return ResponseEntity.ok(new PublicProfileResponse(
				user.getId(),
				user.getDisplayName(),
				user.getHandle(),
				user.getDescription(),
				user.getAvatarGradientStart(),
				user.getAvatarGradientEnd(),
				user.getCreatedAt(),
				sceneResponses));
	}
}
