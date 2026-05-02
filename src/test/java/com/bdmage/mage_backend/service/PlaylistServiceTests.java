package com.bdmage.mage_backend.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.bdmage.mage_backend.exception.AuthenticationRequiredException;
import com.bdmage.mage_backend.exception.PlaylistNotFoundException;
import com.bdmage.mage_backend.exception.SceneOwnershipRequiredException;
import com.bdmage.mage_backend.model.Playlist;
import com.bdmage.mage_backend.model.Scene;
import com.bdmage.mage_backend.model.ScenePlaylist;
import com.bdmage.mage_backend.repository.PlaylistRepository;
import com.bdmage.mage_backend.repository.ScenePlaylistRepository;
import com.bdmage.mage_backend.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PlaylistServiceTests {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void getPlaylistsForUserReturnsAuthenticatedUsersPlaylists() {
		PlaylistRepository playlistRepository = mock(PlaylistRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		PlaylistService playlistService = playlistService(playlistRepository, userRepository);
		Playlist ambientAtlas = playlist(15L, 42L, "Ambient Atlas");
		Playlist nightDrive = playlist(16L, 42L, "Night Drive");

		when(userRepository.existsById(42L)).thenReturn(true);
		when(playlistRepository.findAllByOwnerUserIdOrderByNameAsc(42L))
				.thenReturn(List.of(ambientAtlas, nightDrive));

		List<Playlist> playlists = playlistService.getPlaylistsForUser(42L);

		assertThat(playlists).containsExactly(ambientAtlas, nightDrive);
		verify(playlistRepository).findAllByOwnerUserIdOrderByNameAsc(42L);
	}

	@Test
	void getPlaylistsForUserRejectsMissingAuthenticatedUser() {
		PlaylistRepository playlistRepository = mock(PlaylistRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		PlaylistService playlistService = playlistService(playlistRepository, userRepository);

		assertThatThrownBy(() -> playlistService.getPlaylistsForUser(null))
				.isInstanceOf(AuthenticationRequiredException.class)
				.hasMessage("Authentication is required.");

		verifyNoInteractions(userRepository);
		verifyNoInteractions(playlistRepository);
	}

	@Test
	void attachSceneToPlaylistReturnsImmediatelyWhenPlaylistIdIsNull() throws Exception {
		PlaylistRepository playlistRepository = mock(PlaylistRepository.class);
		ScenePlaylistRepository scenePlaylistRepository = mock(ScenePlaylistRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		PlaylistService playlistService = playlistService(playlistRepository, scenePlaylistRepository, userRepository);
		Scene scene = scene(15L, 42L);

		playlistService.attachSceneToPlaylist(42L, scene, null);

		verifyNoInteractions(userRepository);
		verifyNoInteractions(playlistRepository);
		verifyNoInteractions(scenePlaylistRepository);
	}

	@Test
	void attachSceneToPlaylistPersistsAssociationForOwnedPlaylist() throws Exception {
		PlaylistRepository playlistRepository = mock(PlaylistRepository.class);
		ScenePlaylistRepository scenePlaylistRepository = mock(ScenePlaylistRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		PlaylistService playlistService = playlistService(playlistRepository, scenePlaylistRepository, userRepository);
		Scene scene = scene(15L, 42L);
		Playlist playlist = playlist(7L, 42L, "Favorites");

		when(userRepository.existsById(42L)).thenReturn(true);
		when(playlistRepository.findByIdAndOwnerUserId(7L, 42L)).thenReturn(Optional.of(playlist));
		when(scenePlaylistRepository.existsBySceneIdAndPlaylistId(15L, 7L)).thenReturn(false);
		when(scenePlaylistRepository.saveAndFlush(any(ScenePlaylist.class)))
				.thenAnswer(invocation -> invocation.getArgument(0, ScenePlaylist.class));

		playlistService.attachSceneToPlaylist(42L, scene, 7L);

		ArgumentCaptor<ScenePlaylist> scenePlaylistCaptor = ArgumentCaptor.forClass(ScenePlaylist.class);
		verify(scenePlaylistRepository).saveAndFlush(scenePlaylistCaptor.capture());
		assertThat(scenePlaylistCaptor.getValue().getSceneId()).isEqualTo(15L);
		assertThat(scenePlaylistCaptor.getValue().getPlaylistId()).isEqualTo(7L);
	}

	@Test
	void attachSceneToPlaylistIsIdempotentWhenAssociationAlreadyExists() throws Exception {
		PlaylistRepository playlistRepository = mock(PlaylistRepository.class);
		ScenePlaylistRepository scenePlaylistRepository = mock(ScenePlaylistRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		PlaylistService playlistService = playlistService(playlistRepository, scenePlaylistRepository, userRepository);
		Scene scene = scene(15L, 42L);
		Playlist playlist = playlist(7L, 42L, "Favorites");

		when(userRepository.existsById(42L)).thenReturn(true);
		when(playlistRepository.findByIdAndOwnerUserId(7L, 42L)).thenReturn(Optional.of(playlist));
		when(scenePlaylistRepository.existsBySceneIdAndPlaylistId(15L, 7L)).thenReturn(true);

		playlistService.attachSceneToPlaylist(42L, scene, 7L);

		verify(scenePlaylistRepository, never()).saveAndFlush(any(ScenePlaylist.class));
	}

	@Test
	void attachSceneToPlaylistRejectsMissingOrUnownedPlaylistAsNotFound() throws Exception {
		PlaylistRepository playlistRepository = mock(PlaylistRepository.class);
		ScenePlaylistRepository scenePlaylistRepository = mock(ScenePlaylistRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		PlaylistService playlistService = playlistService(playlistRepository, scenePlaylistRepository, userRepository);
		Scene scene = scene(15L, 42L);

		when(userRepository.existsById(42L)).thenReturn(true);
		when(playlistRepository.findByIdAndOwnerUserId(7L, 42L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> playlistService.attachSceneToPlaylist(42L, scene, 7L))
				.isInstanceOf(PlaylistNotFoundException.class)
				.hasMessage("Playlist not found.");

		verify(scenePlaylistRepository, never()).saveAndFlush(any(ScenePlaylist.class));
	}

	@Test
	void attachSceneToPlaylistRejectsSceneOwnedByAnotherUser() throws Exception {
		PlaylistRepository playlistRepository = mock(PlaylistRepository.class);
		ScenePlaylistRepository scenePlaylistRepository = mock(ScenePlaylistRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		PlaylistService playlistService = playlistService(playlistRepository, scenePlaylistRepository, userRepository);
		Scene scene = scene(15L, 77L);

		when(userRepository.existsById(42L)).thenReturn(true);

		assertThatThrownBy(() -> playlistService.attachSceneToPlaylist(42L, scene, 7L))
				.isInstanceOf(SceneOwnershipRequiredException.class)
				.hasMessage("Scene ownership is required.");

		verifyNoInteractions(playlistRepository);
		verifyNoInteractions(scenePlaylistRepository);
	}

	private PlaylistService playlistService(PlaylistRepository playlistRepository, UserRepository userRepository) {
		return playlistService(playlistRepository, mock(ScenePlaylistRepository.class), userRepository);
	}

	private PlaylistService playlistService(
			PlaylistRepository playlistRepository,
			ScenePlaylistRepository scenePlaylistRepository,
			UserRepository userRepository) {
		return new PlaylistService(playlistRepository, scenePlaylistRepository, userRepository);
	}

	private Playlist playlist(Long playlistId, Long ownerUserId, String name) {
		Playlist playlist = new Playlist(ownerUserId, name);
		ReflectionTestUtils.setField(playlist, "id", playlistId);
		ReflectionTestUtils.setField(playlist, "createdAt", Instant.parse("2026-03-26T15:00:00Z"));
		ReflectionTestUtils.setField(playlist, "updatedAt", Instant.parse("2026-03-26T15:00:00Z"));
		return playlist;
	}

	private Scene scene(Long sceneId, Long ownerUserId) throws Exception {
		Scene scene = new Scene(
				ownerUserId,
				"Aurora Drift",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						"""));
		ReflectionTestUtils.setField(scene, "id", sceneId);
		ReflectionTestUtils.setField(scene, "createdAt", Instant.parse("2026-03-26T15:00:00Z"));
		return scene;
	}
}
