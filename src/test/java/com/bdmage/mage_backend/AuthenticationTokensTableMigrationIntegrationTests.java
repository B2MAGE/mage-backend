package com.bdmage.mage_backend;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
class AuthenticationTokensTableMigrationIntegrationTests extends PostgresIntegrationTestSupport {

	@Autowired
	private DataSource dataSource;

	@Test
	void authTokensTableContainsExpectedColumns() throws Exception {
		try (Connection connection = this.dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement("""
						SELECT column_name
						FROM information_schema.columns
						WHERE table_schema = 'public'
						  AND table_name = 'auth_tokens'
						ORDER BY ordinal_position
						""");
				ResultSet resultSet = statement.executeQuery()) {

			List<String> columns = new ArrayList<>();
			while (resultSet.next()) {
				columns.add(resultSet.getString("column_name"));
			}

			assertThat(columns).containsExactly(
					"id",
					"user_id",
					"token_hash",
					"created_at");
		}
	}

	@Test
	void authTokensTableSupportsUniqueTokenHashesAndUserForeignKeys() throws Exception {
		try (Connection connection = this.dataSource.getConnection()) {
			long userId = insertLocalUser(connection, "auth-token-user-" + System.nanoTime() + "@example.com");
			String tokenHash = "a".repeat(64);

			insertAuthToken(connection, userId, tokenHash);

			assertThatThrownBy(() -> insertAuthToken(connection, userId, tokenHash))
					.isInstanceOf(SQLException.class);

			assertThatThrownBy(() -> insertAuthToken(connection, Long.MAX_VALUE, "b".repeat(64)))
					.isInstanceOf(SQLException.class);
		}
	}

	private long insertLocalUser(Connection connection, String email) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO users (email, password_hash, display_name)
				VALUES (?, ?, ?)
				RETURNING id
				""")) {
			statement.setString(1, email);
			statement.setString(2, "hashed-password-value");
			statement.setString(3, "Token User");

			try (ResultSet resultSet = statement.executeQuery()) {
				assertThat(resultSet.next()).isTrue();
				return resultSet.getLong("id");
			}
		}
	}

	private void insertAuthToken(Connection connection, long userId, String tokenHash) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO auth_tokens (user_id, token_hash)
				VALUES (?, ?)
				""")) {
			statement.setLong(1, userId);
			statement.setString(2, tokenHash);
			statement.executeUpdate();
		}
	}
}
