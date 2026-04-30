package com.bdmage.mage_backend.repository;

import java.util.List;
import java.util.Optional;

import com.bdmage.mage_backend.model.Playlist;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
@Testcontainers
class PlaylistRepositoryIntegrationTests extends PostgresIntegrationTestSupport {

	@Autowired
	private PlaylistRepository playlistRepository;

	@Autowired
	private UserRepository userRepository;

	@Test
	void findAllByOwnerUserIdOrderByNameAscReturnsOnlyOwnedPlaylists() {
		String suffix = String.valueOf(System.nanoTime());
		User owner = this.userRepository.saveAndFlush(
				new User("playlist-owner-" + suffix + "@example.com", "hashed-password-value", "Playlist Owner"));
		User otherOwner = this.userRepository.saveAndFlush(
				new User("playlist-other-" + suffix + "@example.com", "hashed-password-value", "Other Playlist Owner"));

		Playlist nightDrive = this.playlistRepository.saveAndFlush(new Playlist(owner.getId(), "Night Drive"));
		Playlist ambientAtlas = this.playlistRepository.saveAndFlush(new Playlist(owner.getId(), "Ambient Atlas"));
		this.playlistRepository.saveAndFlush(new Playlist(otherOwner.getId(), "Other User Collection"));

		List<Playlist> playlists = this.playlistRepository.findAllByOwnerUserIdOrderByNameAsc(owner.getId());

		assertThat(playlists)
				.extracting(Playlist::getId)
				.containsExactly(ambientAtlas.getId(), nightDrive.getId());
		assertThat(playlists)
				.extracting(Playlist::getName)
				.containsExactly("Ambient Atlas", "Night Drive");
	}

	@Test
	void findByIdAndOwnerUserIdScopesPlaylistLookupToOwner() {
		String suffix = String.valueOf(System.nanoTime());
		User owner = this.userRepository.saveAndFlush(
				new User("playlist-scope-owner-" + suffix + "@example.com", "hashed-password-value", "Playlist Owner"));
		User otherOwner = this.userRepository.saveAndFlush(
				new User("playlist-scope-other-" + suffix + "@example.com", "hashed-password-value", "Other Owner"));
		Playlist playlist = this.playlistRepository.saveAndFlush(new Playlist(owner.getId(), "Favorites"));

		Optional<Playlist> found = this.playlistRepository.findByIdAndOwnerUserId(playlist.getId(), owner.getId());

		assertThat(found).isPresent();
		assertThat(found.get().getId()).isEqualTo(playlist.getId());
		assertThat(found.get().getName()).isEqualTo("Favorites");
		assertThat(this.playlistRepository.findByIdAndOwnerUserId(playlist.getId(), otherOwner.getId()))
				.isEmpty();
		assertThat(this.playlistRepository.existsByIdAndOwnerUserId(playlist.getId(), owner.getId()))
				.isTrue();
		assertThat(this.playlistRepository.existsByIdAndOwnerUserId(playlist.getId(), otherOwner.getId()))
				.isFalse();
	}
}
