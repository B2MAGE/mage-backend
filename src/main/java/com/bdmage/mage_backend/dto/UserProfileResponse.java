package com.bdmage.mage_backend.dto;

import java.time.Instant;

public record UserProfileResponse(
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
		Instant createdAt) {
}
