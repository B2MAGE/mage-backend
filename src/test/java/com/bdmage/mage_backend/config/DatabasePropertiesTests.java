package com.bdmage.mage_backend.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DatabasePropertiesTests
 *
 * This are unit tests for the DatabaseProperties validator.
 *
 * A *unit test* checks a small piece of code in isolation without starting
 * the full Spring Boot application. This makes tests fast and easy to run.
 *
 * These tests verify that our database configuration validation behaves
 * correctly and produces helpful error messages when configuration is wrong.
 *
 * We use:
 * - JUnit → the testing framework
 * - AssertJ → a library that provides readable assertions
 */
class DatabasePropertiesTests {

	/**
	 * Test: rejectsMissingUrl
	 *
	 * This test verifies that the validator rejects a missing database URL.
	 *
	 * In this case, the value "${SPRING_DATASOURCE_URL}" represents an
	 * unresolved environment variable placeholder. This simulates what
	 * happens when the environment variable is not provided.
	 *
	 * The validator should detect this and throw an IllegalStateException.
	 */
	@Test
	void rejectsMissingUrl() {

		DatabaseProperties properties = new DatabaseProperties("${SPRING_DATASOURCE_URL}", "postgres", "postgres");

		/**
		 * assertThatThrownBy(...)
		 *
		 * This assertion verifies that calling validate() throws an exception.
		 */
		assertThatThrownBy(properties::validate)
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("SPRING_DATASOURCE_URL is required.");
	}

	/**
	 * Test: rejectsNonPostgresJdbcUrl
	 *
	 * This test ensures that the application only accepts PostgreSQL
	 * database URLs.
	 *
	 * If someone accidentally configures a MySQL URL, the validator should
	 * reject it and provide a clear error message.
	 */
	@Test
	void rejectsNonPostgresJdbcUrl() {

		DatabaseProperties properties = new DatabaseProperties("jdbc:mysql://localhost:3306/mage", "postgres",
				"postgres");

		assertThatThrownBy(properties::validate)
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("SPRING_DATASOURCE_URL must start with 'jdbc:postgresql:'.");
	}

	/**
	 * Test: rejectsMissingPassword
	 *
	 * This test verifies that the database password must be provided.
	 *
	 * If the password is missing or still contains a placeholder,
	 * the validator should throw an exception instead of allowing
	 * the application to start with invalid configuration.
	 */
	@Test
	void rejectsMissingPassword() {

		DatabaseProperties properties = new DatabaseProperties(
				"jdbc:postgresql://localhost:5432/mage",
				"postgres",
				"${SPRING_DATASOURCE_PASSWORD}");

		assertThatThrownBy(properties::validate)
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("SPRING_DATASOURCE_PASSWORD is required.");
	}

	/**
	 * Test: acceptsValidPostgresSettings
	 *
	 * This test verifies the "happy path".
	 *
	 * When valid PostgreSQL configuration values are provided,
	 * the validator should not throw any exceptions.
	 */
	@Test
	void acceptsValidPostgresSettings() {

		DatabaseProperties properties = new DatabaseProperties(
				"jdbc:postgresql://localhost:5432/mage",
				"postgres",
				"postgres");

		/**
		 * assertThatNoException()
		 *
		 * This assertion verifies that the method completes normally
		 * without throwing an exception.
		 */
		assertThatNoException().isThrownBy(properties::validate);
	}
}