package com.bdmage.mage_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserProfileRequest(
		@NotBlank(message = "firstName must not be blank")
		@Size(max = 100, message = "firstName must be at most 100 characters")
		String firstName,
		@NotBlank(message = "lastName must not be blank")
		@Size(max = 100, message = "lastName must be at most 100 characters")
		String lastName,
		@NotBlank(message = "displayName must not be blank")
		@Size(max = 100, message = "displayName must be at most 100 characters")
		String displayName,
		@NotBlank(message = "handle must not be blank")
		@Pattern(
				regexp = "^@[a-zA-Z][a-zA-Z0-9_]{2,29}$",
				message = "handle must start with @ and contain 3 to 30 letters, numbers, or underscores")
		String handle,
		@Size(max = 300, message = "description must be at most 300 characters")
		String description,
		@Pattern(regexp = "^#[a-fA-F0-9]{6}$", message = "avatarGradientStart must use the #RRGGBB format")
		String avatarGradientStart,
		@Pattern(regexp = "^#[a-fA-F0-9]{6}$", message = "avatarGradientEnd must use the #RRGGBB format")
		String avatarGradientEnd) {
}
