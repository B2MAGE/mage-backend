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
class SceneTagsTableMigrationIntegrationTests extends PostgresIntegrationTestSupport {

	@Autowired
	private DataSource dataSource;

	@Test
	void sceneTagsTableContainsExpectedColumns() throws Exception {
		try (Connection connection = this.dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement("""
						SELECT column_name
						FROM information_schema.columns
						WHERE table_schema = 'public'
						  AND table_name = 'scene_tags'
						ORDER BY ordinal_position
						""");
				ResultSet resultSet = statement.executeQuery()) {

			List<String> columns = new ArrayList<>();
			while (resultSet.next()) {
				columns.add(resultSet.getString("column_name"));
			}

			assertThat(columns).containsExactly(
					"scene_id",
					"tag_id");
		}
	}

	@Test
	void sceneTagsTableEnforcesForeignKeysAndPreventsDuplicatePairs() throws Exception {
		try (Connection connection = this.dataSource.getConnection()) {
			long sceneId = insertScene(connection, "scene-tag-owner-" + System.nanoTime() + "@example.com");
			long tagId = insertTag(connection, "ambient-" + System.nanoTime());

			insertSceneTag(connection, sceneId, tagId);

			assertThatThrownBy(() -> insertSceneTag(connection, sceneId, tagId))
					.isInstanceOf(SQLException.class);

			assertThatThrownBy(() -> insertSceneTag(connection, Long.MAX_VALUE, tagId))
					.isInstanceOf(SQLException.class);

			assertThatThrownBy(() -> insertSceneTag(connection, sceneId, Long.MAX_VALUE))
					.isInstanceOf(SQLException.class);
		}
	}

	private long insertScene(Connection connection, String email) throws SQLException {
		long ownerUserId = insertLocalUser(connection, email);

		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO scenes (owner_user_id, name, scene_data)
				VALUES (?, ?, CAST(? AS jsonb))
				RETURNING id
				""")) {
			statement.setLong(1, ownerUserId);
			statement.setString(2, "Tagged Scene");
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
				INSERT INTO users (email, password_hash, display_name, first_name, last_name)
				VALUES (?, ?, ?, ?, ?)
				RETURNING id
				""")) {
			statement.setString(1, email);
			statement.setString(2, "hashed-password-value");
			statement.setString(3, "Scene Tag Owner");
			statement.setString(4, "Scene Tag");
			statement.setString(5, "Owner");

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

	private void insertSceneTag(Connection connection, long sceneId, long tagId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO scene_tags (scene_id, tag_id)
				VALUES (?, ?)
				""")) {
			statement.setLong(1, sceneId);
			statement.setLong(2, tagId);
			statement.executeUpdate();
		}
	}
}
