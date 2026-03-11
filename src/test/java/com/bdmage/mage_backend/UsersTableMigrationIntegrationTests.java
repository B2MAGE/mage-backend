package com.bdmage.mage_backend;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import com.bdmage.mage_backend.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
@Testcontainers
class UsersTableMigrationIntegrationTests extends PostgresIntegrationTestSupport {

	@Autowired
	private DataSource dataSource;

	@Test
	void usersTableContainsExpectedColumns() throws Exception {
		try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement("""
						SELECT column_name
						FROM information_schema.columns
						WHERE table_schema = 'public'
						  AND table_name = 'users'
						ORDER BY ordinal_position
						""");
				ResultSet resultSet = statement.executeQuery()) {

			List<String> columns = new ArrayList<>();
			while (resultSet.next()) {
				columns.add(resultSet.getString("column_name"));
			}

			assertThat(columns).containsExactly(
					"id",
					"email",
					"password_hash",
					"display_name",
					"created_at",
					"auth_provider",
					"google_subject");
		}
	}

	@Test
	void usersTableSupportsLocalAndGoogleAccountsAndRecordsCreatedAtAutomatically() throws Exception {
		try (Connection connection = dataSource.getConnection()) {
			String sharedEmail = "shared-email-" + System.nanoTime() + "@example.com";
			String googleSubject = "google-subject-" + System.nanoTime();

			Timestamp localCreatedAt = insertLocalUserAndReturnCreatedAt(connection, sharedEmail);
			Timestamp googleCreatedAt = insertGoogleUserAndReturnCreatedAt(connection, sharedEmail, googleSubject);

			assertThat(localCreatedAt).isNotNull();
			assertThat(googleCreatedAt).isNotNull();

			assertThatThrownBy(() -> insertLocalUserAndReturnCreatedAt(connection, sharedEmail))
					.isInstanceOf(SQLException.class);

			assertThatThrownBy(() -> insertGoogleUserAndReturnCreatedAt(connection, "other-" + sharedEmail, googleSubject))
					.isInstanceOf(SQLException.class);
		}
	}

	@Test
	void usersTableRejectsInvalidProviderSpecificFieldCombinations() throws Exception {
		try (Connection connection = dataSource.getConnection()) {
			assertThatThrownBy(() -> insertGoogleUserWithoutSubject(connection))
					.isInstanceOf(SQLException.class);

			assertThatThrownBy(() -> insertLocalUserWithoutPasswordHash(connection))
					.isInstanceOf(SQLException.class);
		}
	}

	private Timestamp insertLocalUserAndReturnCreatedAt(Connection connection, String email) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO users (email, password_hash, display_name)
				VALUES (?, ?, ?)
				RETURNING created_at
				""")) {
			statement.setString(1, email);
			statement.setString(2, "hashed-password-value");
			statement.setString(3, "Test User");

			try (ResultSet resultSet = statement.executeQuery()) {
				assertThat(resultSet.next()).isTrue();
				return resultSet.getTimestamp("created_at");
			}
		}
	}

	private Timestamp insertGoogleUserAndReturnCreatedAt(Connection connection, String email, String googleSubject)
			throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO users (email, auth_provider, password_hash, google_subject, display_name)
				VALUES (?, 'GOOGLE', NULL, ?, ?)
				RETURNING created_at
				""")) {
			statement.setString(1, email);
			statement.setString(2, googleSubject);
			statement.setString(3, "Google User");

			try (ResultSet resultSet = statement.executeQuery()) {
				assertThat(resultSet.next()).isTrue();
				return resultSet.getTimestamp("created_at");
			}
		}
	}

	private void insertGoogleUserWithoutSubject(Connection connection) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO users (email, auth_provider, password_hash, display_name)
				VALUES (?, 'GOOGLE', NULL, ?)
				""")) {
			statement.setString(1, "google-missing-subject-" + System.nanoTime() + "@example.com");
			statement.setString(2, "Google User");
			statement.executeUpdate();
		}
	}

	private void insertLocalUserWithoutPasswordHash(Connection connection) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO users (email, auth_provider, password_hash, display_name)
				VALUES (?, 'LOCAL', NULL, ?)
				""")) {
			statement.setString(1, "local-missing-password-" + System.nanoTime() + "@example.com");
			statement.setString(2, "Local User");
			statement.executeUpdate();
		}
	}
}
