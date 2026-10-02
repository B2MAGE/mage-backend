package com.bdmage.mage_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegistrationRequest(
		@NotBlank(message = "email must not be blank")
		@Email(message = "email must be a well-formed email address")
		@Size(max = 320, message = "email must be at most 320 characters")
		String email,
		@NotBlank(message = "password must not be blank")
		@Size(min = 8, message = "password must be at least 8 characters")
		@Size(max = 72, message = "password must be at most 72 characters")
		String password,
		@NotBlank(message = "firstName must not be blank")
		@Size(max = 100, message = "firstName must be at most 100 characters")
		String firstName,
		@NotBlank(message = "lastName must not be blank")
		@Size(max = 100, message = "lastName must be at most 100 characters")
		String lastName,
		@NotBlank(message = "displayName must not be blank")
		@Size(min = 2, message = "displayName must be at least 2 characters")
		@Size(max = 100, message = "displayName must be at most 100 characters")
		String displayName,
		@NotBlank(message = "handle must not be blank")
		@Pattern(
				regexp = "^@[a-zA-Z][a-zA-Z0-9_]{2,29}$",
				message = "handle must start with @ and contain 3 to 30 letters, numbers, or underscores")
		String handle) {
	public RegistrationRequest {
		displayName = displayName == null ? null : displayName.trim();
	}
}
