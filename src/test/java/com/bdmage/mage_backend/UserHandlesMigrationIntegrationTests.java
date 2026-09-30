package com.bdmage.mage_backend;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import com.bdmage.mage_backend.support.PostgresIntegrationTestSupport;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
@Testcontainers
class UserHandlesMigrationIntegrationTests extends PostgresIntegrationTestSupport {

	@Autowired
	private DataSource dataSource;

	@Test
	void migrationBackfillsReadableUniqueHandlesForExistingUsers() throws Exception {
		String schema = "handle_backfill_" + Long.toUnsignedString(System.nanoTime(), 36);
		createSchema(schema);

		try {
			migrate(schema, MigrationVersion.fromVersion("15"));
			insertPreHandleUser(schema, "same-one@example.com", "Same Name");
			insertPreHandleUser(schema, "same-two@example.com", "Same Name");
			insertPreHandleUser(schema, "odd.person@example.com", "12");

			migrate(schema, null);

			assertThat(readHandles(schema)).containsExactly(
					"samename",
					"samename_2",
					"oddperson");
		} finally {
			dropSchema(schema);
		}
	}

	private void migrate(String schema, MigrationVersion target) {
		var configuration = Flyway.configure()
				.dataSource(this.dataSource)
				.defaultSchema(schema)
				.schemas(schema);
		if (target != null) {
			configuration.target(target);
		}
		configuration.load().migrate();
	}

	private void createSchema(String schema) throws Exception {
		try (Connection connection = this.dataSource.getConnection();
				Statement statement = connection.createStatement()) {
			statement.execute("CREATE SCHEMA " + schema);
		}
	}

	private void dropSchema(String schema) throws Exception {
		try (Connection connection = this.dataSource.getConnection();
				Statement statement = connection.createStatement()) {
			statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
		}
	}

	private void insertPreHandleUser(String schema, String email, String displayName) throws Exception {
		try (Connection connection = this.dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement("""
						INSERT INTO %s.users (email, password_hash, display_name, first_name, last_name)
						VALUES (?, ?, ?, ?, ?)
						""".formatted(schema))) {
			statement.setString(1, email);
			statement.setString(2, "hashed-password-value");
			statement.setString(3, displayName);
			statement.setString(4, displayName);
			statement.setString(5, "User");
			statement.executeUpdate();
		}
	}

	private List<String> readHandles(String schema) throws Exception {
		try (Connection connection = this.dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement("""
						SELECT handle
						FROM %s.users
						ORDER BY id
						""".formatted(schema));
				ResultSet resultSet = statement.executeQuery()) {
			List<String> handles = new ArrayList<>();
			while (resultSet.next()) {
				handles.add(resultSet.getString("handle"));
			}
			return handles;
		}
	}
}
