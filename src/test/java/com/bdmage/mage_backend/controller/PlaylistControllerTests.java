package com.bdmage.mage_backend.controller;

import java.time.Instant;
import java.util.List;

import com.bdmage.mage_backend.config.AuthenticatedUserRequest;
import com.bdmage.mage_backend.exception.ApiExceptionHandler;
import com.bdmage.mage_backend.exception.AuthenticationRequiredException;
import com.bdmage.mage_backend.model.Playlist;
import com.bdmage.mage_backend.service.PlaylistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlaylistControllerTests {

	private PlaylistService playlistService;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		this.playlistService = mock(PlaylistService.class);
		this.mockMvc = MockMvcBuilders
				.standaloneSetup(new PlaylistController(this.playlistService))
				.setControllerAdvice(new ApiExceptionHandler())
				.build();
	}

	@Test
	void getPlaylistsReturnsLightweightAuthenticatedUsersPlaylistOptions() throws Exception {
		when(this.playlistService.getPlaylistsForUser(42L))
				.thenReturn(List.of(
						playlist(15L, 42L, "Ambient Atlas"),
						playlist(16L, 42L, "Night Drive")));

		this.mockMvc.perform(get("/api/playlists")
				.requestAttr(AuthenticatedUserRequest.USER_ID_ATTRIBUTE, 42L))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$[0].id").value(15L))
				.andExpect(jsonPath("$[0].name").value("Ambient Atlas"))
				.andExpect(jsonPath("$[0].ownerUserId").doesNotExist())
				.andExpect(jsonPath("$[0].createdAt").doesNotExist())
				.andExpect(jsonPath("$[0].updatedAt").doesNotExist())
				.andExpect(jsonPath("$[1].id").value(16L))
				.andExpect(jsonPath("$[1].name").value("Night Drive"))
				.andExpect(jsonPath("$[1].ownerUserId").doesNotExist())
				.andExpect(jsonPath("$[1].createdAt").doesNotExist())
				.andExpect(jsonPath("$[1].updatedAt").doesNotExist());
	}

	@Test
	void getPlaylistsReturnsUnauthorizedWhenRequestIsNotAuthenticated() throws Exception {
		when(this.playlistService.getPlaylistsForUser(null))
				.thenThrow(new AuthenticationRequiredException("Authentication is required."));

		this.mockMvc.perform(get("/api/playlists"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
				.andExpect(jsonPath("$.message").value("Authentication is required."));
	}

	private Playlist playlist(Long playlistId, Long ownerUserId, String name) {
		Playlist playlist = new Playlist(ownerUserId, name);
		ReflectionTestUtils.setField(playlist, "id", playlistId);
		ReflectionTestUtils.setField(playlist, "createdAt", Instant.parse("2026-03-26T15:00:00Z"));
		ReflectionTestUtils.setField(playlist, "updatedAt", Instant.parse("2026-03-26T15:00:00Z"));
		return playlist;
	}
}
