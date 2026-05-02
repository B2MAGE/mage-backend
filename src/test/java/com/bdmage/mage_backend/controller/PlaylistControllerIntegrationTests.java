package com.bdmage.mage_backend.controller;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.bdmage.mage_backend.model.Playlist;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.repository.PlaylistRepository;
import com.bdmage.mage_backend.repository.UserRepository;
import com.bdmage.mage_backend.service.PasswordHashingService;
import com.bdmage.mage_backend.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PlaylistControllerIntegrationTests extends PostgresIntegrationTestSupport {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PlaylistRepository playlistRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordHashingService passwordHashingService;

	@Test
	void getPlaylistsReturnsOnlyAuthenticatedUsersPlaylists() throws Exception {
		String suffix = String.valueOf(System.nanoTime());
		String email = "playlist-list-user-" + suffix + "@example.com";
		String password = "password-" + suffix;
		User savedUser = this.userRepository.saveAndFlush(new User(
				email,
				this.passwordHashingService.hash(password),
				"Playlist List User"));
		User otherUser = this.userRepository.saveAndFlush(new User(
				"playlist-list-other-" + suffix + "@example.com",
				this.passwordHashingService.hash("other-password-" + suffix),
				"Other Playlist User"));
		Playlist nightDrive = this.playlistRepository.saveAndFlush(new Playlist(savedUser.getId(), "Night Drive"));
		Playlist ambientAtlas = this.playlistRepository.saveAndFlush(new Playlist(savedUser.getId(), "Ambient Atlas"));
		this.playlistRepository.saveAndFlush(new Playlist(otherUser.getId(), "Hidden Other Playlist"));

		String accessToken = accessToken(this.mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequestBody(email, password)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn());

		this.mockMvc.perform(get("/api/playlists")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$[0].id").value(ambientAtlas.getId()))
				.andExpect(jsonPath("$[0].name").value("Ambient Atlas"))
				.andExpect(jsonPath("$[0].ownerUserId").doesNotExist())
				.andExpect(jsonPath("$[0].createdAt").doesNotExist())
				.andExpect(jsonPath("$[0].updatedAt").doesNotExist())
				.andExpect(jsonPath("$[1].id").value(nightDrive.getId()))
				.andExpect(jsonPath("$[1].name").value("Night Drive"))
				.andExpect(jsonPath("$[1].ownerUserId").doesNotExist())
				.andExpect(jsonPath("$[1].createdAt").doesNotExist())
				.andExpect(jsonPath("$[1].updatedAt").doesNotExist())
				.andExpect(content().string(not(containsString("Hidden Other Playlist"))));
	}

	@Test
	void getPlaylistsRejectsUnauthenticatedRequest() throws Exception {
		this.mockMvc.perform(get("/api/playlists")
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
				.andExpect(jsonPath("$.message").value("Authentication is required."));
	}

	private static String loginRequestBody(String email, String password) {
		return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
	}

	private static String accessToken(MvcResult result) throws Exception {
		Matcher matcher = Pattern.compile("\"accessToken\":\"([^\"]+)\"")
				.matcher(result.getResponse().getContentAsString());
		assertThat(matcher.find()).isTrue();
		return matcher.group(1);
	}
}
