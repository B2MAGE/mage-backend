package com.bdmage.mage_backend.service;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ReadinessServiceTests
 *
 * This are unit tests for the ReadinessService.
 *
 * A *unit test* checks a small piece of code in isolation without starting
 * the full Spring Boot application. This makes tests fast and easy to run.
 *
 * The goal of these tests is to verify the decision logic that determines
 * whether the application is ready to serve requests.
 *
 * In these tests we mock:
 *
 * • ApplicationAvailability → tells us whether Spring is accepting traffic
 * • DataSource → provides database connections
 * • Connection → represents the database connection itself
 *
 * This allows us to simulate different scenarios without starting Spring
 * or connecting to a real database.
 */
class ReadinessServiceTests {

	/**
	 * Test: reportsReadyWhenAppAcceptsTrafficAndDatabaseIsUp
	 *
	 * The application should be considered READY only when:
	 *
	 * 1) Spring Boot says the application is accepting traffic
	 * 2) The database connection is valid
	 */
	@Test
	void reportsReadyWhenAppAcceptsTrafficAndDatabaseIsUp() throws Exception {

		/*
		 * Create mock dependencies.
		 * These are fake objects whose behavior we control in the test.
		 */
		ApplicationAvailability applicationAvailability = mock(ApplicationAvailability.class);
		DataSource dataSource = mock(DataSource.class);
		Connection connection = mock(Connection.class);

		/*
		 * Create the service being tested.
		 */
		ReadinessService readinessService = new ReadinessService(applicationAvailability, dataSource);

		/*
		 * Configure the mock behavior:
		 *
		 * - Spring reports it is accepting traffic
		 * - A database connection can be obtained
		 * - The connection reports itself as valid
		 */
		when(applicationAvailability.getReadinessState())
				.thenReturn(ReadinessState.ACCEPTING_TRAFFIC);

		when(dataSource.getConnection())
				.thenReturn(connection);

		when(connection.isValid(1))
				.thenReturn(true);

		/*
		 * Call the method we want to test.
		 */
		ReadinessService.ReadinessStatus readiness = readinessService.checkReadiness();

		/*
		 * Verify the results.
		 */
		assertThat(readiness.ready()).isTrue();
		assertThat(readiness.database()).isEqualTo("UP");

		/*
		 * Verify that the connection was closed.
		 * This ensures the service does not leak database connections.
		 */
		verify(connection).close();
	}

	/**
	 * Test: reportsNotReadyWhenAppIsNotAcceptingTraffic
	 *
	 * Even if the database is working, the application should NOT be ready
	 * if Spring Boot is currently refusing traffic.
	 *
	 * This can happen during:
	 * • startup
	 * • shutdown
	 * • maintenance states
	 */
	@Test
	void reportsNotReadyWhenAppIsNotAcceptingTraffic() throws Exception {

		ApplicationAvailability applicationAvailability = mock(ApplicationAvailability.class);
		DataSource dataSource = mock(DataSource.class);
		Connection connection = mock(Connection.class);

		ReadinessService readinessService = new ReadinessService(applicationAvailability, dataSource);

		when(applicationAvailability.getReadinessState())
				.thenReturn(ReadinessState.REFUSING_TRAFFIC);

		when(dataSource.getConnection())
				.thenReturn(connection);

		when(connection.isValid(1))
				.thenReturn(true);

		ReadinessService.ReadinessStatus readiness = readinessService.checkReadiness();

		assertThat(readiness.ready()).isFalse();
		assertThat(readiness.database()).isEqualTo("UP");

		verify(connection).close();
	}

	/**
	 * Test: reportsDatabaseDownWhenConnectionProbeFails
	 *
	 * If the database connection cannot be obtained, the service should
	 * report the database as DOWN.
	 *
	 * This simulates a real failure such as:
	 * • database server offline
	 * • network failure
	 * • authentication failure
	 */
	@Test
	void reportsDatabaseDownWhenConnectionProbeFails() throws Exception {

		ApplicationAvailability applicationAvailability = mock(ApplicationAvailability.class);
		DataSource dataSource = mock(DataSource.class);

		ReadinessService readinessService = new ReadinessService(applicationAvailability, dataSource);

		when(applicationAvailability.getReadinessState())
				.thenReturn(ReadinessState.ACCEPTING_TRAFFIC);

		/*
		 * Simulate a database failure by throwing an exception
		 * when a connection is requested.
		 */
		when(dataSource.getConnection())
				.thenThrow(new SQLException("Database unavailable"));

		ReadinessService.ReadinessStatus readiness = readinessService.checkReadiness();

		assertThat(readiness.ready()).isFalse();
		assertThat(readiness.database()).isEqualTo("DOWN");
	}
}