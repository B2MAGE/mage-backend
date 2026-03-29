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
class PresetTagsTableMigrationIntegrationTests extends PostgresIntegrationTestSupport {

	@Autowired
	private DataSource dataSource;

	@Test
	void presetTagsTableContainsExpectedColumns() throws Exception {
		try (Connection connection = this.dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement("""
						SELECT column_name
						FROM information_schema.columns
						WHERE table_schema = 'public'
						  AND table_name = 'preset_tags'
						ORDER BY ordinal_position
						""");
				ResultSet resultSet = statement.executeQuery()) {

			List<String> columns = new ArrayList<>();
			while (resultSet.next()) {
				columns.add(resultSet.getString("column_name"));
			}

			assertThat(columns).containsExactly(
					"preset_id",
					"tag_id");
		}
	}

	@Test
	void presetTagsTableEnforcesForeignKeysAndPreventsDuplicatePairs() throws Exception {
		try (Connection connection = this.dataSource.getConnection()) {
			long presetId = insertPreset(connection, "preset-tag-owner-" + System.nanoTime() + "@example.com");
			long tagId = insertTag(connection, "ambient-" + System.nanoTime());

			insertPresetTag(connection, presetId, tagId);

			assertThatThrownBy(() -> insertPresetTag(connection, presetId, tagId))
					.isInstanceOf(SQLException.class);

			assertThatThrownBy(() -> insertPresetTag(connection, Long.MAX_VALUE, tagId))
					.isInstanceOf(SQLException.class);

			assertThatThrownBy(() -> insertPresetTag(connection, presetId, Long.MAX_VALUE))
					.isInstanceOf(SQLException.class);
		}
	}

	private long insertPreset(Connection connection, String email) throws SQLException {
		long ownerUserId = insertLocalUser(connection, email);

		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO presets (owner_user_id, name, scene_data)
				VALUES (?, ?, CAST(? AS jsonb))
				RETURNING id
				""")) {
			statement.setLong(1, ownerUserId);
			statement.setString(2, "Tagged Preset");
			statement.setString(3, """
					{"visualizer":{"shader":"nebula"}}
					""");

			try (ResultSet resultSet = statement.executeQuery()) {
				assertThat(resultSet.next()).isTrue();
				return resultSet.getLong("id");
			}
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
			statement.setString(3, "Preset Tag Owner");

			try (ResultSet resultSet = statement.executeQuery()) {
				assertThat(resultSet.next()).isTrue();
				return resultSet.getLong("id");
			}
		}
	}

	private long insertTag(Connection connection, String name) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO tags (name)
				VALUES (?)
				RETURNING id
				""")) {
			statement.setString(1, name);

			try (ResultSet resultSet = statement.executeQuery()) {
				assertThat(resultSet.next()).isTrue();
				return resultSet.getLong("id");
			}
		}
	}

	private void insertPresetTag(Connection connection, long presetId, long tagId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO preset_tags (preset_id, tag_id)
				VALUES (?, ?)
				""")) {
			statement.setLong(1, presetId);
			statement.setLong(2, tagId);
			statement.executeUpdate();
		}
	}
}
