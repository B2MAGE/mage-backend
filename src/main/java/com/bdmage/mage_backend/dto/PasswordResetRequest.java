package com.bdmage.mage_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetRequest(
		@NotBlank(message = "email must not be blank")
		@Email(message = "email must be a well-formed email address")
		@Size(max = 320, message = "email must be at most 320 characters")
		String email) {
}
