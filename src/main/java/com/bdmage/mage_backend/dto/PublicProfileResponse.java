package com.bdmage.mage_backend.dto;

import java.time.Instant;
import java.util.List;

public record PublicProfileResponse(
		Long userId,
		String displayName,
		String handle,
		String description,
		String avatarGradientStart,
		String avatarGradientEnd,
		Instant createdAt,
		List<SceneResponse> scenes) {
}
