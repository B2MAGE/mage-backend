package com.bdmage.mage_backend.config;

import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mage.scene-availability")
public record SceneAvailabilityProperties(
		Set<Long> operatorUserIds,
		boolean customRenderingReleaseApproved) {

	public SceneAvailabilityProperties {
		operatorUserIds = operatorUserIds == null ? Set.of() : Set.copyOf(operatorUserIds);
		if (operatorUserIds.stream().anyMatch(id -> id <= 0)) {
			throw new IllegalArgumentException("Operator user IDs must be positive.");
		}
	}
}
