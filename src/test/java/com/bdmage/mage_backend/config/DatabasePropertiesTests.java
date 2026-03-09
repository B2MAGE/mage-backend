package com.bdmage.mage_backend.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabasePropertiesTests {

	@Test
	void rejectsMissingUrl() {
		DatabaseProperties properties = new DatabaseProperties("${SPRING_DATASOURCE_URL}", "postgres", "postgres");

		assertThatThrownBy(properties::validate)
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("SPRING_DATASOURCE_URL is required.");
	}

	@Test
	void rejectsNonPostgresJdbcUrl() {
		DatabaseProperties properties = new DatabaseProperties("jdbc:mysql://localhost:3306/mage", "postgres", "postgres");

		assertThatThrownBy(properties::validate)
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("SPRING_DATASOURCE_URL must start with 'jdbc:postgresql:'.");
	}

	@Test
	void rejectsMissingPassword() {
		DatabaseProperties properties = new DatabaseProperties("jdbc:postgresql://localhost:5432/mage", "postgres",
			"${SPRING_DATASOURCE_PASSWORD}");

		assertThatThrownBy(properties::validate)
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("SPRING_DATASOURCE_PASSWORD is required.");
	}

	@Test
	void acceptsValidPostgresSettings() {
		DatabaseProperties properties = new DatabaseProperties("jdbc:postgresql://localhost:5432/mage", "postgres",
			"postgres");

		assertThatNoException().isThrownBy(properties::validate);
	}
}
