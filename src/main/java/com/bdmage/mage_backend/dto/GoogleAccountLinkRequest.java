package com.bdmage.mage_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GoogleAccountLinkRequest(
		@NotBlank(message = "email must not be blank")
		@Email(message = "email must be a well-formed email address")
		@Size(max = 320, message = "email must be at most 320 characters")
		String email,
		@NotBlank(message = "password must not be blank")
		@Size(max = 72, message = "password must be at most 72 characters")
		String password,
		@NotBlank(message = "idToken must not be blank")
		String idToken) {
}
