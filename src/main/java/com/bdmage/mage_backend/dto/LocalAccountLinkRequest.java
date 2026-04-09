package com.bdmage.mage_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LocalAccountLinkRequest(
		@NotBlank(message = "idToken must not be blank")
		String idToken,
		@NotBlank(message = "password must not be blank")
		@Size(max = 72, message = "password must be at most 72 characters")
		String password) {
}
