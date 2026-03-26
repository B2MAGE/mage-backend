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
class PresetsTableMigrationIntegrationTests extends PostgresIntegrationTestSupport {

	@Autowired
	private DataSource dataSource;

	@Test
	void presetsTableContainsExpectedColumns() throws Exception {
		try (Connection connection = this.dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement("""
						SELECT column_name
						FROM information_schema.columns
						WHERE table_schema = 'public'
						  AND table_name = 'presets'
						ORDER BY ordinal_position
						""");
				ResultSet resultSet = statement.executeQuery()) {

			List<String> columns = new ArrayList<>();
			while (resultSet.next()) {
				columns.add(resultSet.getString("column_name"));
			}

			assertThat(columns).containsExactly(
					"id",
					"owner_user_id",
					"name",
					"scene_data",
					"thumbnail_ref",
					"created_at");
		}
	}

	@Test
	void presetsTableStoresSceneDataAsJsonbAndEnforcesOwnerForeignKeys() throws Exception {
		try (Connection connection = this.dataSource.getConnection()) {
			long ownerUserId = insertLocalUser(connection, "preset-owner-" + System.nanoTime() + "@example.com");
			long presetId = insertPreset(connection, ownerUserId, """
					{"visualizer":{"shader":"nebula"},"state":{"energy":0.92}}
					""", "thumbnails/preset-1.png");

			PresetRow savedPreset = loadPresetRow(connection, presetId);

			assertThat(savedPreset.createdAt()).isNotNull();
			assertThat(savedPreset.thumbnailRef()).isEqualTo("thumbnails/preset-1.png");
			assertThat(savedPreset.shader()).isEqualTo("nebula");
			assertThat(savedPreset.sceneDataType()).isEqualTo("jsonb");

			assertThatThrownBy(() -> insertPreset(connection, Long.MAX_VALUE, """
					{"visualizer":{"shader":"invalid-owner"}}
					""", null))
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
			statement.setString(3, "Preset Owner");

			try (ResultSet resultSet = statement.executeQuery()) {
				assertThat(resultSet.next()).isTrue();
				return resultSet.getLong("id");
			}
		}
	}

	private long insertPreset(Connection connection, long ownerUserId, String sceneData, String thumbnailRef)
			throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO presets (owner_user_id, name, scene_data, thumbnail_ref)
				VALUES (?, ?, CAST(? AS jsonb), ?)
				RETURNING id
				""")) {
			statement.setLong(1, ownerUserId);
			statement.setString(2, "Aurora Drift");
			statement.setString(3, sceneData);
			statement.setString(4, thumbnailRef);

			try (ResultSet resultSet = statement.executeQuery()) {
				assertThat(resultSet.next()).isTrue();
				return resultSet.getLong("id");
			}
		}
	}

	private PresetRow loadPresetRow(Connection connection, long presetId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT created_at,
				       thumbnail_ref,
				       scene_data -> 'visualizer' ->> 'shader' AS shader,
				       pg_typeof(scene_data)::text AS scene_data_type
				FROM presets
				WHERE id = ?
				""")) {
			statement.setLong(1, presetId);

			try (ResultSet resultSet = statement.executeQuery()) {
				assertThat(resultSet.next()).isTrue();
				return new PresetRow(
						resultSet.getTimestamp("created_at"),
						resultSet.getString("thumbnail_ref"),
						resultSet.getString("shader"),
						resultSet.getString("scene_data_type"));
			}
		}
	}

	private record PresetRow(
			Timestamp createdAt,
			String thumbnailRef,
			String shader,
			String sceneDataType) {
	}
}
