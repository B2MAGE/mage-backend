package com.bdmage.mage_backend.dto;

import java.time.Instant;

public record CustomRenderingControlResponse(
		boolean enabled, boolean releaseApproved, Long changedByUserId, Instant changedAt, String reason) {
}
