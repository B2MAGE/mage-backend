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

			assertThat(columns).containsExactly("id", "email", "password_hash", "display_name", "created_at");
		}
	}

	@Test
	void usersTableEnforcesUniqueEmailAndRecordsCreatedAtAutomatically() throws Exception {
		try (Connection connection = dataSource.getConnection()) {
			String firstEmail = "unique-email-" + System.nanoTime() + "@example.com";

			Timestamp createdAt = insertUserAndReturnCreatedAt(connection, firstEmail);

			assertThat(createdAt).isNotNull();

			assertThatThrownBy(() -> insertUserAndReturnCreatedAt(connection, firstEmail))
					.isInstanceOf(SQLException.class);
		}
	}

	private Timestamp insertUserAndReturnCreatedAt(Connection connection, String email) throws SQLException {
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
}
