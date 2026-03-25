package com.bdmage.mage_backend.dto;

public record RegistrationResponse(
		Long userId,
		String email,
		String displayName,
		String authProvider) {
}
