package com.bdmage.mage_backend.dto;

public record GoogleAuthenticationResponse(
		Long userId,
		String email,
		String firstName,
		String lastName,
		String displayName,
		String handle,
		String description,
		String avatarGradientStart,
		String avatarGradientEnd,
		String authProvider,
		boolean created,
		String accessToken) {
}
