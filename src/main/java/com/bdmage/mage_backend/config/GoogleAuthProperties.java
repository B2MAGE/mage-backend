package com.bdmage.mage_backend.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "mage.auth.google")
public record GoogleAuthProperties(String clientIds) {

	public void validate() {
		requireValue("MAGE_AUTH_GOOGLE_CLIENT_IDS", clientIds);

		if (allowedClientIds().isEmpty()) {
			throw new IllegalStateException(
					"MAGE_AUTH_GOOGLE_CLIENT_IDS must contain at least one Google OAuth client ID.");
		}
	}

	public List<String> allowedClientIds() {
		if (!StringUtils.hasText(clientIds)) {
			return List.of();
		}

		return Arrays.stream(clientIds.split(","))
				.map(String::trim)
				.filter(StringUtils::hasText)
				.toList();
	}

	private static void requireValue(String environmentVariable, String value) {
		if (!StringUtils.hasText(value) || isUnresolvedPlaceholder(value)) {
			throw new IllegalStateException(environmentVariable + " is required.");
		}
	}

	private static boolean isUnresolvedPlaceholder(String value) {
		return value.startsWith("${") && value.endsWith("}");
	}
}
