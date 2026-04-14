package com.bdmage.mage_backend.controller;

import java.util.List;
import com.bdmage.mage_backend.config.AuthenticatedUserRequest;
import com.bdmage.mage_backend.dto.SceneResponse;
import com.bdmage.mage_backend.dto.UserProfileResponse;
import com.bdmage.mage_backend.model.Scene;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.service.SceneService;
import com.bdmage.mage_backend.service.SceneResponseFactory;
import com.bdmage.mage_backend.service.UserProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

	private final SceneService sceneService;
	private final UserProfileService userProfileService;
	private final SceneResponseFactory sceneResponseFactory;

	public UserController(
			SceneService sceneService,
			UserProfileService userProfileService,
			SceneResponseFactory sceneResponseFactory) {
		this.sceneService = sceneService;
		this.userProfileService = userProfileService;
		this.sceneResponseFactory = sceneResponseFactory;
	}

	@GetMapping("/me")
	ResponseEntity<UserProfileResponse> me(
			@RequestAttribute(name = AuthenticatedUserRequest.USER_ID_ATTRIBUTE, required = false) Long authenticatedUserId) {
		User user = this.userProfileService.getAuthenticatedUser(authenticatedUserId);

		return ResponseEntity.ok(new UserProfileResponse(
				user.getId(),
				user.getEmail(),
				user.getDisplayName(),
				user.getAuthProvider().name(),
				user.getCreatedAt()));
	}

	@GetMapping("/{userId}/scenes")
	ResponseEntity<List<SceneResponse>> scenes(
			@PathVariable Long userId,
			@RequestAttribute(name = AuthenticatedUserRequest.USER_ID_ATTRIBUTE, required = false) Long authenticatedUserId) {
		List<Scene> scenes = this.sceneService.getScenesForUser(authenticatedUserId, userId);

		return ResponseEntity.ok(this.sceneResponseFactory.from(scenes));
	}
}
