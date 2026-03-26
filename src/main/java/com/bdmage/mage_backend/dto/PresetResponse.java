package com.bdmage.mage_backend.dto;

import java.time.Instant;
import java.util.Map;

public record PresetResponse(
		Long presetId,
		Long ownerUserId,
		String name,
		Map<String, Object> sceneData,
		String thumbnailRef,
		Instant createdAt) {
}
