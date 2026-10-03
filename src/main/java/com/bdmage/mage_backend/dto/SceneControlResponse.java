package com.bdmage.mage_backend.dto;

import java.time.Instant;

public record SceneControlResponse(
		Long sceneId, boolean disabled, Long changedByUserId, Instant changedAt, String reason) {
}
