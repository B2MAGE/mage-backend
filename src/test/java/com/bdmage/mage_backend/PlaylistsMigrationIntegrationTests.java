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
class PlaylistsMigrationIntegrationTests extends PostgresIntegrationTestSupport {

	@Autowired
	private DataSource dataSource;

	@Test
	void playlistsTableContainsExpectedColumns() throws Exception {
		try (Connection connection = this.dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement("""
						SELECT column_name
						FROM information_schema.columns
						WHERE table_schema = 'public'
						  AND table_name = 'playlists'
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
					"created_at",
					"updated_at");
		}
	}

	@Test
	void scenePlaylistsTableContainsExpectedColumnsAndRejectsDuplicates() throws Exception {
		try (Connection connection = this.dataSource.getConnection()) {
			long ownerUserId = insertLocalUser(connection, "playlist-owner-" + System.nanoTime() + "@example.com");
			long sceneId = insertScene(connection, ownerUserId);
			long playlistId = insertPlaylist(connection, ownerUserId, "Favorites");

			insertScenePlaylist(connection, sceneId, playlistId);

			assertThatThrownBy(() -> insertScenePlaylist(connection, sceneId, playlistId))
					.isInstanceOf(SQLException.class);
		}
	}

	@Test
	void playlistForeignKeysRequireExistingOwnerSceneAndPlaylist() throws Exception {
		try (Connection connection = this.dataSource.getConnection()) {
			long ownerUserId = insertLocalUser(connection, "playlist-fk-owner-" + System.nanoTime() + "@example.com");
			long sceneId = insertScene(connection, ownerUserId);
			long playlistId = insertPlaylist(connection, ownerUserId, "Live Sets");

			assertThatThrownBy(() -> insertPlaylist(connection, Long.MAX_VALUE, "Invalid Owner"))
					.isInstanceOf(SQLException.class);
			assertThatThrownBy(() -> insertScenePlaylist(connection, Long.MAX_VALUE, playlistId))
					.isInstanceOf(SQLException.class);
			assertThatThrownBy(() -> insertScenePlaylist(connection, sceneId, Long.MAX_VALUE))
					.isInstanceOf(SQLException.class);
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
			statement.setString(3, "Playlist Owner");
			statement.setString(4, "Playlist");
			statement.setString(5, "Owner");

			try (ResultSet resultSet = statement.executeQuery()) {
				assertThat(resultSet.next()).isTrue();
				return resultSet.getLong("id");
			}
		}
	}

	private long insertScene(Connection connection, long ownerUserId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO scenes (owner_user_id, name, scene_data)
				VALUES (?, ?, CAST(? AS jsonb))
				RETURNING id
				""")) {
			statement.setLong(1, ownerUserId);
			statement.setString(2, "Aurora Drift");
			statement.setString(3, "{\"visualizer\":{\"shader\":\"nebula\"}}");

			try (ResultSet resultSet = statement.executeQuery()) {
				assertThat(resultSet.next()).isTrue();
				return resultSet.getLong("id");
			}
		}
	}

	private long insertPlaylist(Connection connection, long ownerUserId, String name) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO playlists (owner_user_id, name)
				VALUES (?, ?)
				RETURNING id
				""")) {
			statement.setLong(1, ownerUserId);
			statement.setString(2, name);

			try (ResultSet resultSet = statement.executeQuery()) {
				assertThat(resultSet.next()).isTrue();
				return resultSet.getLong("id");
			}
		}
	}

	private void insertScenePlaylist(Connection connection, long sceneId, long playlistId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO scene_playlists (scene_id, playlist_id)
				VALUES (?, ?)
				""")) {
			statement.setLong(1, sceneId);
			statement.setLong(2, playlistId);
			statement.executeUpdate();
		}
	}
}
