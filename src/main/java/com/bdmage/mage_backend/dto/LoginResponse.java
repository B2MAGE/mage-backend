package com.bdmage.mage_backend.dto;

public record LoginResponse(
		Long userId,
		String email,
		String firstName,
		String lastName,
		String displayName,
		String handle,
		String description,
		String authProvider,
		String accessToken) {
}
