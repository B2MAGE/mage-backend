package com.bdmage.mage_backend.model;

import java.time.Instant;

public record SceneAvailabilityControl(
		Long sceneId, boolean disabled, Long changedByUserId, Instant changedAt, String reason) {
}
