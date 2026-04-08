package com.bdmage.mage_backend.dto;

public record LoginResponse(
		Long userId,
		String email,
		String displayName,
		String authProvider,
		String accessToken) {
}
