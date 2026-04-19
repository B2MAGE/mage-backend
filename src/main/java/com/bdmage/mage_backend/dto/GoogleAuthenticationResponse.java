package com.bdmage.mage_backend.dto;

public record GoogleAuthenticationResponse(
		Long userId,
		String email,
		String firstName,
		String lastName,
		String displayName,
		String authProvider,
		boolean created,
		String accessToken) {
}
