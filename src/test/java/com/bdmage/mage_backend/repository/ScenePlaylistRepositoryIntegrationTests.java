package com.bdmage.mage_backend.repository;

import com.bdmage.mage_backend.model.Playlist;
import com.bdmage.mage_backend.model.Scene;
import com.bdmage.mage_backend.model.ScenePlaylist;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.support.PostgresIntegrationTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
@Testcontainers
class ScenePlaylistRepositoryIntegrationTests extends PostgresIntegrationTestSupport {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Autowired
	private ScenePlaylistRepository scenePlaylistRepository;

	@Autowired
	private SceneRepository sceneRepository;

	@Autowired
	private PlaylistRepository playlistRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@PersistenceContext
	private EntityManager entityManager;

	@Test
	void savePersistsScenePlaylistPairAndSupportsExistenceAndCountLookups() throws Exception {
		User owner = this.userRepository.saveAndFlush(new User(
				"scene-playlist-owner-" + System.nanoTime() + "@example.com",
				"hashed-password-value",
				"Scene Playlist Owner"));
		Scene scene = this.sceneRepository.saveAndFlush(new Scene(
				owner.getId(),
				"Aurora Drift",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						""")));
		Playlist playlist = this.playlistRepository.saveAndFlush(new Playlist(owner.getId(), "Favorites"));

		ScenePlaylist savedScenePlaylist = this.scenePlaylistRepository.saveAndFlush(
				new ScenePlaylist(scene.getId(), playlist.getId()));

		this.entityManager.clear();

		assertThat(savedScenePlaylist.getSceneId()).isEqualTo(scene.getId());
		assertThat(savedScenePlaylist.getPlaylistId()).isEqualTo(playlist.getId());
		assertThat(this.scenePlaylistRepository.existsBySceneIdAndPlaylistId(scene.getId(), playlist.getId())).isTrue();
		assertThat(this.scenePlaylistRepository.countBySceneIdAndPlaylistId(scene.getId(), playlist.getId()))
				.isEqualTo(1L);
	}

	@Test
	void rejectsDuplicateScenePlaylistPairs() throws Exception {
		User owner = this.userRepository.saveAndFlush(new User(
				"scene-playlist-duplicate-owner-" + System.nanoTime() + "@example.com",
				"hashed-password-value",
				"Scene Playlist Duplicate Owner"));
		Scene scene = this.sceneRepository.saveAndFlush(new Scene(
				owner.getId(),
				"Glass Orbit",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"glass"}}
						""")));
		Playlist playlist = this.playlistRepository.saveAndFlush(new Playlist(owner.getId(), "Favorites"));

		this.scenePlaylistRepository.saveAndFlush(new ScenePlaylist(scene.getId(), playlist.getId()));
		this.entityManager.clear();

		TransactionTemplate transactionTemplate = new TransactionTemplate(this.transactionManager);

		assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
			this.entityManager.persist(new ScenePlaylist(scene.getId(), playlist.getId()));
			this.entityManager.flush();
		})).isInstanceOf(PersistenceException.class);
	}
}
