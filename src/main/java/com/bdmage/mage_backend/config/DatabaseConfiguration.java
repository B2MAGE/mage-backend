package com.bdmage.mage_backend.config;

import java.sql.SQLException;
import java.time.Duration;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContextException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * DatabaseConfiguration
 *
 * This class tells Spring Boot how to create and configure the database
 * connection used by the application.
 *
 * In Spring Boot, classes marked with @Configuration act like a "setup file"
 * where we define objects (called Beans) that the rest of the application can
 * use.
 *
 * In this case, we are creating a DataSource, which is the component
 * responsible
 * for connecting to the PostgreSQL database.
 *
 * The DataSource will later be used by things like:
 * - Spring Data JPA
 * - Repositories
 * - Any service that needs to read/write database data
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DatabaseProperties.class)
public class DatabaseConfiguration {

	/**
	 * dataSource(...)
	 *
	 * This method creates the DataSource object that the application will use
	 * to communicate with PostgreSQL.
	 *
	 * The @Bean annotation tells Spring:
	 *
	 * "Create this object once and make it available for dependency injection."
	 *
	 * Other parts of the application can then automatically receive this DataSource
	 * without manually creating it.
	 */
	@Bean
	DataSource dataSource(DatabaseProperties properties) {

		// First we verify that the required database settings exist
		// (URL, username, password, etc.)
		properties.validate();

		/**
		 * HikariCP is the connection pool used by Spring Boot.
		 *
		 * Instead of opening a brand new database connection every time the app
		 * needs data, Hikari maintains a pool of reusable connections.
		 *
		 * This dramatically improves performance and reduces database overhead.
		 */
		HikariConfig config = new HikariConfig();

		// These values come from our configuration properties
		config.setJdbcUrl(properties.url());
		config.setUsername(properties.username());
		config.setPassword(properties.password());

		// This name will appear in logs when the connection pool starts
		config.setPoolName("mage-backend");

		/**
		 * These timeout settings prevent the application from hanging forever
		 * if the database is unreachable.
		 */

		// Maximum time to wait for a connection
		config.setConnectionTimeout(Duration.ofSeconds(10).toMillis());

		// Maximum time to wait while validating a connection
		config.setValidationTimeout(Duration.ofSeconds(5).toMillis());

		// Fail startup if the database cannot be reached within this time
		config.setInitializationFailTimeout(Duration.ofSeconds(10).toMillis());

		// Create the connection pool
		HikariDataSource dataSource = new HikariDataSource(config);

		try {

			/**
			 * Here we open a connection during application startup.
			 *
			 * Why do this?
			 *
			 * If the database credentials or host are wrong, we want the
			 * application to fail immediately instead of waiting until the
			 * first real request tries to use the database.
			 */
			dataSource.getConnection().close();

			return dataSource;
		} catch (SQLException ex) {

			// If the connection fails, we clean up the pool
			dataSource.close();

			/**
			 * ApplicationContextException stops Spring Boot from starting.
			 *
			 * This produces a clear error message that helps developers
			 * quickly diagnose configuration problems.
			 */
			throw new ApplicationContextException(
					"Unable to connect to the configured PostgreSQL database. Verify "
							+ "SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME, and "
							+ "SPRING_DATASOURCE_PASSWORD.",
					ex);
		}
	}
}