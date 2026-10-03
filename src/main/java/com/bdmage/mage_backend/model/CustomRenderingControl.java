package com.bdmage.mage_backend.model;

import java.time.Instant;

public record CustomRenderingControl(
		boolean enabled, Long changedByUserId, Instant changedAt, String reason) {
}
