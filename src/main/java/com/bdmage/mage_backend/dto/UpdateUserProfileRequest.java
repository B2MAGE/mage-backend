package com.bdmage.mage_backend.dto;

import jakarta.validation.constraints.NotBlank;
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
		String displayName) {
}
