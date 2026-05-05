package com.bdmage.mage_backend.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "mage.password-reset")
public record PasswordResetProperties(
		PasswordResetDeliveryMode delivery,
		String frontendBaseUrl,
		Duration tokenTtl,
		String fromEmail,
		String fromName) {

	private static final String DEFAULT_FRONTEND_BASE_URL = "http://localhost:5173";
	private static final Duration DEFAULT_TOKEN_TTL = Duration.ofMinutes(30);
	private static final String DEFAULT_FROM_NAME = "MAGE";

	public PasswordResetDeliveryMode resolvedDelivery() {
		return this.delivery == null ? PasswordResetDeliveryMode.LOG : this.delivery;
	}

	public String normalizedFrontendBaseUrl() {
		if (!StringUtils.hasText(this.frontendBaseUrl)) {
			return DEFAULT_FRONTEND_BASE_URL;
		}

		return this.frontendBaseUrl.trim().replaceAll("/+$", "");
	}

	public Duration resolvedTokenTtl() {
		if (this.tokenTtl == null || this.tokenTtl.isNegative() || this.tokenTtl.isZero()) {
			return DEFAULT_TOKEN_TTL;
		}

		return this.tokenTtl;
	}

	public String resolvedFromName() {
		if (!StringUtils.hasText(this.fromName)) {
			return DEFAULT_FROM_NAME;
		}

		return this.fromName.trim();
	}
}
