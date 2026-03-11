package com.bdmage.mage_backend;

import java.sql.Connection;

import com.bdmage.mage_backend.support.PostgresIntegrationTestSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MageBackendApplicationTests
 *
 * This is a **high-level integration test** that starts the entire Spring Boot
 * application.
 *
 * Unlike unit tests (which test a single class), this test verifies that the
 * whole application can start correctly with its real configuration.
 *
 * It checks things like:
 * • Spring Boot can create all required beans
 * • configuration classes load correctly
 * • the datasource connects to PostgreSQL
 * • there are no startup errors
 *
 * These tests are useful because they catch wiring/configuration problems
 * before a teammate discovers them while running the application manually.
 */
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)

/*
 * @SpringBootTest starts the entire Spring Boot application context.
 * This means our controllers, services, and configuration classes are
 * created the same way they would be in the real application.
 */
@SpringBootTest

/*
 * @Testcontainers tells the test framework that this test uses Docker
 * containers provided by Testcontainers.
 *
 * The PostgreSQL container itself is defined in the parent class
 * PostgresIntegrationTestSupport.
 */
@Testcontainers
class MageBackendApplicationTests extends PostgresIntegrationTestSupport {

	/**
	 * Spring automatically injects the configured DataSource here.
	 *
	 * The DataSource is the main object the application uses to obtain
	 * database connections.
	 */
	@Autowired
	private DataSource dataSource;

	@Autowired
	private Flyway flyway;

	/**
	 * Test: contextLoadsWithPostgres
	 *
	 * This test verifies two things:
	 *
	 * 1) The Spring Boot application starts successfully.
	 * 2) The datasource can obtain a working connection to PostgreSQL.
	 * 3) Flyway applies the initial schema migration during startup.
	 *
	 * If either of these steps fails, the test will fail.
	 */
	@Test
	void contextLoadsWithPostgres() throws Exception {

		/*
		 * Request a database connection from the datasource.
		 * The try-with-resources block ensures the connection is closed
		 * automatically after the test finishes.
		 */
		try (Connection connection = dataSource.getConnection()) {

			/*
			 * connection.isValid(1) asks the JDBC driver to confirm that the
			 * database connection is usable within 1 second.
			 */
			assertThat(connection.isValid(1)).isTrue();
		}

		assertThat(flyway.info().current()).isNotNull();
		assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("2");
		assertThat(flyway.info().current().getDescription()).isEqualTo("create users table");
	}

}
