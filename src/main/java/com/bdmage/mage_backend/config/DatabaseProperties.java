package com.bdmage.mage_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "spring.datasource")
public record DatabaseProperties(String url, String username, String password) {

	public void validate() {
		requireValue("SPRING_DATASOURCE_URL", url);
		if (!url.startsWith("jdbc:postgresql:")) {
			throw new IllegalStateException("SPRING_DATASOURCE_URL must start with 'jdbc:postgresql:'.");
		}
		requireValue("SPRING_DATASOURCE_USERNAME", username);
		requireValue("SPRING_DATASOURCE_PASSWORD", password);
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
