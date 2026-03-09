package com.bdmage.mage_backend.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgresIntegrationTestSupport
 *
 * This is a **shared base class for integration tests that need a PostgreSQL
 * database**.
 *
 * Instead of requiring everyone running the tests to install PostgreSQL
 * locally,
 * we use **Testcontainers**, which starts a temporary PostgreSQL Docker
 * container
 * automatically when the tests run.
 *
 * This has several advantages:
 *
 * • Tests run against a real database (not a mock)
 * • Every test run starts with a clean database
 * • No manual database setup is required
 * • Tests behave more like the real production environment
 *
 * Any integration test that extends this class automatically gets access to
 * this temporary PostgreSQL database.
 */
public abstract class PostgresIntegrationTestSupport {

	/**
	 * This defines the PostgreSQL container used during the tests.
	 *
	 * @Container tells Testcontainers that this object represents a Docker
	 *            container that should be started before the tests run.
	 *
	 *            The container uses the official PostgreSQL Docker image.
	 */
	@Container
	@SuppressWarnings("resource")
	static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16"))

			/*
			 * Configure the database inside the container.
			 *
			 * These values are only used for the test environment.
			 */
			.withDatabaseName("mage_test")
			.withUsername("postgres")
			.withPassword("postgres");

	/**
	 * registerDatasourceProperties
	 *
	 * Spring Boot normally reads database configuration from properties like:
	 *
	 * spring.datasource.url
	 * spring.datasource.username
	 * spring.datasource.password
	 *
	 * Because the PostgreSQL container chooses its port dynamically,
	 * we cannot hardcode these values in application.properties.
	 *
	 * @DynamicPropertySource lets us provide these values programmatically
	 *                        after the container starts.
	 *
	 *                        Spring then uses these values to configure the
	 *                        DataSource for the test.
	 */
	@DynamicPropertySource
	static void registerDatasourceProperties(DynamicPropertyRegistry registry) {

		/*
		 * Provide the JDBC connection URL from the container.
		 */
		registry.add("spring.datasource.url", postgres::getJdbcUrl);

		/*
		 * Provide the database username.
		 */
		registry.add("spring.datasource.username", postgres::getUsername);

		/*
		 * Provide the database password.
		 */
		registry.add("spring.datasource.password", postgres::getPassword);
	}
}