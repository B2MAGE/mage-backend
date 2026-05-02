package com.bdmage.mage_backend.service;

import java.util.List;

import com.bdmage.mage_backend.exception.AuthenticationRequiredException;
import com.bdmage.mage_backend.exception.PlaylistNotFoundException;
import com.bdmage.mage_backend.exception.SceneOwnershipRequiredException;
import com.bdmage.mage_backend.model.Playlist;
import com.bdmage.mage_backend.model.Scene;
import com.bdmage.mage_backend.model.ScenePlaylist;
import com.bdmage.mage_backend.repository.PlaylistRepository;
import com.bdmage.mage_backend.repository.ScenePlaylistRepository;
import com.bdmage.mage_backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlaylistService {

	private static final String AUTHENTICATION_REQUIRED_MESSAGE = "Authentication is required.";
	private static final String PLAYLIST_NOT_FOUND_MESSAGE = "Playlist not found.";
	private static final String SCENE_OWNERSHIP_REQUIRED_MESSAGE = "Scene ownership is required.";

	private final PlaylistRepository playlistRepository;
	private final ScenePlaylistRepository scenePlaylistRepository;
	private final UserRepository userRepository;

	public PlaylistService(
			PlaylistRepository playlistRepository,
			ScenePlaylistRepository scenePlaylistRepository,
			UserRepository userRepository) {
		this.playlistRepository = playlistRepository;
		this.scenePlaylistRepository = scenePlaylistRepository;
		this.userRepository = userRepository;
	}

	@Transactional(readOnly = true)
	public List<Playlist> getPlaylistsForUser(Long authenticatedUserId) {
		requireAuthenticatedUser(authenticatedUserId);
		return this.playlistRepository.findAllByOwnerUserIdOrderByNameAsc(authenticatedUserId);
	}

	@Transactional
	public void attachSceneToPlaylist(Long authenticatedUserId, Scene scene, Long playlistId) {
		if (playlistId == null) {
			return;
		}

		requireAuthenticatedUser(authenticatedUserId);
		requireSceneOwnership(scene, authenticatedUserId);

		Playlist playlist = this.playlistRepository.findByIdAndOwnerUserId(playlistId, authenticatedUserId)
				.orElseThrow(() -> new PlaylistNotFoundException(PLAYLIST_NOT_FOUND_MESSAGE));

		if (this.scenePlaylistRepository.existsBySceneIdAndPlaylistId(scene.getId(), playlist.getId())) {
			return;
		}

		this.scenePlaylistRepository.saveAndFlush(new ScenePlaylist(scene.getId(), playlist.getId()));
	}

	private void requireAuthenticatedUser(Long authenticatedUserId) {
		if (authenticatedUserId == null || !this.userRepository.existsById(authenticatedUserId)) {
			throw new AuthenticationRequiredException(AUTHENTICATION_REQUIRED_MESSAGE);
		}
	}

	private static void requireSceneOwnership(Scene scene, Long authenticatedUserId) {
		if (!scene.getOwnerUserId().equals(authenticatedUserId)) {
			throw new SceneOwnershipRequiredException(SCENE_OWNERSHIP_REQUIRED_MESSAGE);
		}
	}
}
