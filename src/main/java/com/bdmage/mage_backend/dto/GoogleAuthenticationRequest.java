package com.bdmage.mage_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record GoogleAuthenticationRequest(
		@NotBlank(message = "idToken must not be blank")
		String idToken,
		@Pattern(
				regexp = "^@[a-zA-Z][a-zA-Z0-9_]{2,29}$",
				message = "handle must start with @ and contain 3 to 30 letters, numbers, or underscores")
		String handle) {
}
