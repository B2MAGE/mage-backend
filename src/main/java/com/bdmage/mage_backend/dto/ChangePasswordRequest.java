package com.bdmage.mage_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
		@NotBlank(message = "currentPassword must not be blank")
		@Size(max = 72, message = "currentPassword must be at most 72 characters")
		String currentPassword,
		@NotBlank(message = "newPassword must not be blank")
		@Size(min = 8, max = 72, message = "newPassword must be between 8 and 72 characters")
		String newPassword) {
}
