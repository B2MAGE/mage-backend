package com.bdmage.mage_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmRequest(
		@NotBlank(message = "token must not be blank")
		@Size(max = 255, message = "token must be at most 255 characters")
		String token,
		@NotBlank(message = "newPassword must not be blank")
		@Size(min = 8, max = 72, message = "newPassword must be between 8 and 72 characters")
		String newPassword) {
}
