package com.bdmage.mage_backend.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleAuthPropertiesTests {

	@Test
	void rejectsMissingClientIds() {
		GoogleAuthProperties properties = new GoogleAuthProperties("${MAGE_AUTH_GOOGLE_CLIENT_IDS}");

		assertThatThrownBy(properties::validate)
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("MAGE_AUTH_GOOGLE_CLIENT_IDS is required.");
	}

	@Test
	void rejectsBlankClientIdsAfterParsing() {
		GoogleAuthProperties properties = new GoogleAuthProperties(" , ");

		assertThatThrownBy(properties::validate)
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("MAGE_AUTH_GOOGLE_CLIENT_IDS must contain at least one Google OAuth client ID.");
	}

	@Test
	void parsesCommaSeparatedClientIds() {
		GoogleAuthProperties properties = new GoogleAuthProperties(
				"web-client.apps.googleusercontent.com, ios-client.apps.googleusercontent.com ");

		assertThatNoException().isThrownBy(properties::validate);
		assertThat(properties.allowedClientIds()).containsExactly(
				"web-client.apps.googleusercontent.com",
				"ios-client.apps.googleusercontent.com");
	}
}
