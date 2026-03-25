package com.bdmage.mage_backend.controller;

import com.bdmage.mage_backend.config.AuthenticatedUserRequest;
import com.bdmage.mage_backend.dto.UserProfileResponse;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.service.UserProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {

	private final UserProfileService userProfileService;

	public UserController(UserProfileService userProfileService) {
		this.userProfileService = userProfileService;
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
}
