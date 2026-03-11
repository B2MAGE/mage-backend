package com.bdmage.mage_backend.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleAuthenticationRequest(
		@NotBlank(message = "idToken must not be blank")
		String idToken) {
}
