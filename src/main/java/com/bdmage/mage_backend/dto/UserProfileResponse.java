package com.bdmage.mage_backend.dto;

import java.time.Instant;

public record UserProfileResponse(
		Long userId,
		String email,
		String displayName,
		String authProvider,
		Instant createdAt) {
}
