package com.bdmage.mage_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmRequest(
		@NotBlank(message = "resetToken must not be blank")
		String resetToken,
		@NotBlank(message = "newPassword must not be blank")
		@Size(max = 72, message = "newPassword must be at most 72 characters")
		String newPassword) {
}
