package com.bdmage.mage_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * DatabaseProperties
 *
 * This class stores the database configuration values that Spring Boot reads
 * from the application's configuration.
 *
 * In Spring Boot, values from files like:
 *
 * application.properties
 *
 * or environment variables like:
 *
 * SPRING_DATASOURCE_URL
 * SPRING_DATASOURCE_USERNAME
 * SPRING_DATASOURCE_PASSWORD
 *
 * can automatically be mapped into Java objects.
 *
 * The @ConfigurationProperties annotation tells Spring:
 *
 * "Take all configuration values that start with 'spring.datasource'
 * and place them into this object."
 *
 * Example mapping:
 *
 * spring.datasource.url -> url
 * spring.datasource.username -> username
 * spring.datasource.password -> password
 *
 * This class is defined as a Java *record*, which is a compact way of storing
 * related data values without writing boilerplate getters or constructors.
 */
@ConfigurationProperties(prefix = "spring.datasource")
public record DatabaseProperties(String url, String username, String password) {

	/**
	 * validate()
	 *
	 * This method checks that the required database configuration values exist
	 * before the application tries to connect to the database.
	 *
	 * Without this validation, the application might start successfully but
	 * later fail when the first database query runs. By validating early,
	 * configuration problems are detected during application startup.
	 */
	public void validate() {

		// Ensure the database URL exists
		requireValue("SPRING_DATASOURCE_URL", url);

		/**
		 * The URL must use the PostgreSQL JDBC format.
		 *
		 * Example:
		 * jdbc:postgresql://localhost:5432/mage
		 *
		 * This check helps prevent accidental misconfiguration such as
		 * using the wrong database driver.
		 */
		if (!url.startsWith("jdbc:postgresql:")) {
			throw new IllegalStateException(
					"SPRING_DATASOURCE_URL must start with 'jdbc:postgresql:'.");
		}

		// Ensure username and password are provided
		requireValue("SPRING_DATASOURCE_USERNAME", username);
		requireValue("SPRING_DATASOURCE_PASSWORD", password);
	}

	/**
	 * requireValue(...)
	 *
	 * Helper method used to verify that a configuration value is present.
	 *
	 * This method rejects:
	 * - empty strings
	 * - null values
	 * - unresolved placeholders like ${SOME_ENV_VAR}
	 *
	 * Spring leaves placeholders in this format if an environment variable
	 * is missing when the application starts.
	 */
	private static void requireValue(String environmentVariable, String value) {

		if (!StringUtils.hasText(value) || isUnresolvedPlaceholder(value)) {
			throw new IllegalStateException(environmentVariable + " is required.");
		}
	}

	/**
	 * isUnresolvedPlaceholder(...)
	 *
	 * Checks whether the value still contains an unresolved placeholder.
	 *
	 * Example placeholder:
	 *
	 * ${SPRING_DATASOURCE_URL}
	 *
	 * If Spring cannot find the environment variable, it leaves the value in
	 * this format instead of replacing it with the actual value.
	 *
	 * Detecting this helps produce clearer error messages for developers.
	 */
	private static boolean isUnresolvedPlaceholder(String value) {
		return value.startsWith("${") && value.endsWith("}");
	}
}