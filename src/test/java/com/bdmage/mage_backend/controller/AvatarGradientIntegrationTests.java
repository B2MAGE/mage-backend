package com.bdmage.mage_backend.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import com.bdmage.mage_backend.model.Scene;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.repository.SceneRepository;
import com.bdmage.mage_backend.repository.UserRepository;
import com.bdmage.mage_backend.service.AuthenticationTokenService;
import com.bdmage.mage_backend.service.PasswordHashingService;
import com.bdmage.mage_backend.support.PostgresIntegrationTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AvatarGradientIntegrationTests extends PostgresIntegrationTestSupport {

	private final ObjectMapper mapper = new ObjectMapper();
	@Autowired private MockMvc mvc;
	@Autowired private UserRepository users;
	@Autowired private SceneRepository scenes;
	@Autowired private AuthenticationTokenService tokens;
	@Autowired private PasswordHashingService passwords;

	@Test
	void savedGradientPersistsAndIsReturnedOnEveryPublicIdentitySurface() throws Exception {
		String suffix = Long.toUnsignedString(System.nanoTime(), 36);
		User user = users.saveAndFlush(new User("avatar-" + suffix + "@example.com", passwords.hash("password123"),
				"Avatar", "Artist", "Avatar Artist", "avatar_" + suffix));
		String authorization = "Bearer " + tokens.issueToken(user);
		Map<String, Object> profile = profile(user);
		profile.put("avatarGradientStart", "#AABBCC");
		profile.put("avatarGradientEnd", "#223344");
		mvc.perform(put("/api/users/me").header("Authorization", authorization)
				.contentType("application/json").content(mapper.writeValueAsString(profile)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.avatarGradientStart").value("#aabbcc"))
				.andExpect(jsonPath("$.avatarGradientEnd").value("#223344"));

		User saved = users.findById(user.getId()).orElseThrow();
		assertThat(saved.getAvatarGradientStart()).isEqualTo("#aabbcc");
		assertThat(saved.getAvatarGradientEnd()).isEqualTo("#223344");
		mvc.perform(get("/api/users/me").header("Authorization", authorization))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.avatarGradientStart").value("#aabbcc"))
				.andExpect(jsonPath("$.avatarGradientEnd").value("#223344"));
		mvc.perform(post("/api/auth/login").contentType("application/json")
				.content(mapper.writeValueAsString(Map.of("email", user.getEmail(), "password", "password123"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.avatarGradientStart").value("#aabbcc"))
				.andExpect(jsonPath("$.avatarGradientEnd").value("#223344"));

		Scene scene = scenes.saveAndFlush(new Scene(user.getId(), "Gradient test", mapper.createObjectNode()));
		mvc.perform(get("/api/profiles/" + user.getHandle()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.avatarGradientStart").value("#aabbcc"))
				.andExpect(jsonPath("$.avatarGradientEnd").value("#223344"))
				.andExpect(jsonPath("$.scenes[0].creatorAvatarGradientStart").value("#aabbcc"))
				.andExpect(jsonPath("$.scenes[0].creatorAvatarGradientEnd").value("#223344"));
		mvc.perform(get("/api/scenes/" + scene.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.creatorAvatarGradientStart").value("#aabbcc"))
				.andExpect(jsonPath("$.creatorAvatarGradientEnd").value("#223344"));
		mvc.perform(get("/api/users/" + user.getId() + "/scenes").header("Authorization", authorization))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].creatorAvatarGradientStart").value("#aabbcc"))
				.andExpect(jsonPath("$[0].creatorAvatarGradientEnd").value("#223344"));

		String commentJson = mvc.perform(post("/api/scenes/" + scene.getId() + "/comments")
				.header("Authorization", authorization).contentType("application/json").content("{\"text\":\"A color test\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.authorAvatarGradientStart").value("#aabbcc"))
				.andExpect(jsonPath("$.authorAvatarGradientEnd").value("#223344"))
				.andReturn().getResponse().getContentAsString();
		long parentId = mapper.readTree(commentJson).get("commentId").asLong();
		mvc.perform(post("/api/scenes/" + scene.getId() + "/comments")
				.header("Authorization", authorization).contentType("application/json")
				.content(mapper.writeValueAsString(Map.of("text", "A reply", "parentCommentId", parentId))))
				.andExpect(status().isCreated());
		mvc.perform(get("/api/scenes/" + scene.getId() + "/comments"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].authorAvatarGradientStart").value("#aabbcc"))
				.andExpect(jsonPath("$[0].authorAvatarGradientEnd").value("#223344"))
				.andExpect(jsonPath("$[0].replies[0].authorAvatarGradientStart").value("#aabbcc"))
				.andExpect(jsonPath("$[0].replies[0].authorAvatarGradientEnd").value("#223344"));

		// Older clients omit both fields; null is also a no-op rather than resetting colors.
		for (boolean explicitNull : new boolean[] {false, true}) {
			Map<String, Object> legacyProfile = profile(user);
			if (explicitNull) {
				legacyProfile.put("avatarGradientStart", null);
				legacyProfile.put("avatarGradientEnd", null);
			}
			mvc.perform(put("/api/users/me").header("Authorization", authorization)
					.contentType("application/json").content(mapper.writeValueAsString(legacyProfile)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.avatarGradientStart").value("#aabbcc"))
					.andExpect(jsonPath("$.avatarGradientEnd").value("#223344"));
		}
	}

	@Test
	void gradientUpdatesRequireAuthentication() throws Exception {
		mvc.perform(put("/api/users/me").contentType("application/json").content("""
				{"firstName":"Ari","lastName":"Rivera","displayName":"Ari","handle":"@aririvera",
				 "avatarGradientStart":"#aabbcc","avatarGradientEnd":"#112233"}
				"""))
				.andExpect(status().isUnauthorized());
	}

	private Map<String, Object> profile(User user) {
		return new LinkedHashMap<>(Map.of("firstName", user.getFirstName(), "lastName", user.getLastName(),
				"displayName", user.getDisplayName(), "handle", "@" + user.getHandle()));
	}
}
