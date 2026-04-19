package com.bdmage.mage_backend.dto;

public record RegistrationResponse(
		Long userId,
		String email,
		String firstName,
		String lastName,
		String displayName,
		String authProvider) {
}
