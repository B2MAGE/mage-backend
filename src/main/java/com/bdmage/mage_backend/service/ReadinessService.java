package com.bdmage.mage_backend.service;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.stereotype.Service;

/**
 * ReadinessService
 *
 * In a typical backend application, we separate responsibilities into layers:
 *
 * Controller → handles HTTP requests
 * Service → contains application logic
 * Repository → handles database access
 *
 * This class is part of the "service layer".
 *
 * Its job is to determine whether the application is *ready* to handle
 * incoming requests.
 *
 * This is different from a simple "is the server running?" check.
 * Even if the server process is alive, it may not be able to serve requests
 * if something important (like the database) is unavailable.
 *
 * Platforms like Docker, Kubernetes, or load balancers often call readiness
 * endpoints to decide whether traffic should be sent to this server.
 */
@Service
public class ReadinessService {

	/**
	 * Spring provides ApplicationAvailability to report the current lifecycle
	 * state of the application.
	 *
	 * For example, during startup the app may not yet be ready to accept traffic.
	 */
	private final ApplicationAvailability applicationAvailability;

	/**
	 * DataSource represents the application's connection pool for the database.
	 *
	 * We use it here to test whether the database is reachable.
	 */
	private final DataSource dataSource;

	/**
	 * Spring automatically provides these dependencies through
	 * constructor injection.
	 *
	 * This is called *dependency injection* and allows components
	 * to remain loosely coupled.
	 */
	public ReadinessService(ApplicationAvailability applicationAvailability, DataSource dataSource) {
		this.applicationAvailability = applicationAvailability;
		this.dataSource = dataSource;
	}

	/**
	 * checkReadiness()
	 *
	 * Determines whether the application is ready to serve requests.
	 *
	 * Two conditions must be true:
	 *
	 * 1. Spring reports that the application is accepting traffic
	 * 2. The database connection is working
	 *
	 * If either of these fails, the application is considered "not ready".
	 */
	public ReadinessStatus checkReadiness() {

		// Check if the database connection works
		boolean databaseReady = isDatabaseReady();

		// Check if Spring believes the application is ready to accept traffic
		boolean acceptingTraffic = this.applicationAvailability.getReadinessState() == ReadinessState.ACCEPTING_TRAFFIC;

		// Both conditions must be true for the application to be considered ready
		return new ReadinessStatus(
				acceptingTraffic && databaseReady,
				databaseReady ? "UP" : "DOWN");
	}

	/**
	 * isDatabaseReady()
	 *
	 * Attempts to open a connection from the connection pool and verify
	 * that it is valid.
	 *
	 * If the database is unreachable or credentials are incorrect,
	 * this method will return false.
	 */
	private boolean isDatabaseReady() {

		try (Connection connection = this.dataSource.getConnection()) {

			/**
			 * isValid(timeoutSeconds)
			 *
			 * Asks the JDBC driver to confirm that the connection is usable.
			 * The timeout prevents the check from hanging indefinitely.
			 */
			return connection.isValid(1);

		} catch (SQLException ex) {

			// Any database error means the application is not ready
			return false;
		}
	}

	/**
	 * ReadinessStatus
	 *
	 * A small data object used internally to return the results of the
	 * readiness check.
	 *
	 * ready → overall readiness of the application
	 * database → status of the database connection
	 */
	public record ReadinessStatus(boolean ready, String database) {
	}
}