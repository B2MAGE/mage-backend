package com.bdmage.mage_backend.controller;

import java.time.Instant;
import java.util.List;
import com.bdmage.mage_backend.config.AuthenticatedUserRequest;
import com.bdmage.mage_backend.exception.ApiExceptionHandler;
import com.bdmage.mage_backend.exception.AuthenticationRequiredException;
import com.bdmage.mage_backend.model.Scene;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.repository.UserRepository;
import com.bdmage.mage_backend.service.SceneService;
import com.bdmage.mage_backend.service.SceneResponseFactory;
import com.bdmage.mage_backend.service.UserProfileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerTests {

	private final ObjectMapper objectMapper = new ObjectMapper();

	private SceneService sceneService;
	private UserProfileService userProfileService;
	private UserRepository userRepository;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		this.sceneService = mock(SceneService.class);
		this.userProfileService = mock(UserProfileService.class);
		this.userRepository = mock(UserRepository.class);
		SceneResponseFactory sceneResponseFactory = new SceneResponseFactory(this.userRepository);
		this.mockMvc = MockMvcBuilders
				.standaloneSetup(new UserController(this.sceneService, this.userProfileService, sceneResponseFactory))
				.setControllerAdvice(new ApiExceptionHandler())
				.build();
	}

	@Test
	void meReturnsAuthenticatedUserProfileWithoutSensitiveFields() throws Exception {
		User user = new User("profile-user@example.com", "hashed-password", "Profile", "User", "Profile User");
		ReflectionTestUtils.setField(user, "id", 51L);
		ReflectionTestUtils.setField(user, "createdAt", Instant.parse("2026-03-25T20:15:30Z"));

		when(this.userProfileService.getAuthenticatedUser(51L)).thenReturn(user);

		this.mockMvc.perform(get("/api/users/me")
				.requestAttr(AuthenticatedUserRequest.USER_ID_ATTRIBUTE, 51L))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.userId").value(51L))
				.andExpect(jsonPath("$.email").value("profile-user@example.com"))
				.andExpect(jsonPath("$.firstName").value("Profile"))
				.andExpect(jsonPath("$.lastName").value("User"))
				.andExpect(jsonPath("$.displayName").value("Profile User"))
				.andExpect(jsonPath("$.authProvider").value("LOCAL"))
				.andExpect(jsonPath("$.createdAt").value("2026-03-25T20:15:30Z"))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist())
				.andExpect(jsonPath("$.googleSubject").doesNotExist());
	}

	@Test
	void meReturnsUnauthorizedWhenNoAuthenticatedRequestIdentityExists() throws Exception {
		when(this.userProfileService.getAuthenticatedUser(null))
				.thenThrow(new AuthenticationRequiredException("Authentication is required."));

		this.mockMvc.perform(get("/api/users/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
				.andExpect(jsonPath("$.message").value("Authentication is required."));
	}

	@Test
	void updateMeReturnsUpdatedProfile() throws Exception {
		User user = new User("profile-user@example.com", "hashed-password", "Updated", "Name", "Profile User");
		ReflectionTestUtils.setField(user, "id", 51L);
		ReflectionTestUtils.setField(user, "createdAt", Instant.parse("2026-03-25T20:15:30Z"));

		when(this.userProfileService.updateAuthenticatedUserProfile(eq(51L), eq("Updated"), eq("Name"), eq("Updated Profile")))
				.thenReturn(user);

		this.mockMvc.perform(put("/api/users/me")
				.requestAttr(AuthenticatedUserRequest.USER_ID_ATTRIBUTE, 51L)
				.contentType("application/json")
				.content("""
						{"firstName":"Updated","lastName":"Name","displayName":"Updated Profile"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.firstName").value("Updated"))
				.andExpect(jsonPath("$.lastName").value("Name"))
				.andExpect(jsonPath("$.displayName").value("Profile User"));
	}

	@Test
	void updateMeRejectsInvalidRequestBody() throws Exception {
		this.mockMvc.perform(put("/api/users/me")
				.requestAttr(AuthenticatedUserRequest.USER_ID_ATTRIBUTE, 51L)
				.contentType("application/json")
				.content("""
						{"firstName":" ","lastName":" ","displayName":" "}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.details.firstName").value("firstName must not be blank"))
				.andExpect(jsonPath("$.details.lastName").value("lastName must not be blank"))
				.andExpect(jsonPath("$.details.displayName").value("displayName must not be blank"));
	}

	@Test
	void scenesReturnsRequestedUsersScenes() throws Exception {
		Scene firstScene = scene(
				15L,
				77L,
				"Aurora Drift",
				"Soft teal bloom with low-end drift.",
				Instant.parse("2026-03-26T15:30:00Z"));
		Scene secondScene = scene(16L, 77L, "Signal Bloom", Instant.parse("2026-03-26T16:45:00Z"));

		when(this.sceneService.getScenesForUser(51L, 77L))
				.thenReturn(List.of(firstScene, secondScene));
		when(this.userRepository.findAllById(java.util.Set.of(77L)))
				.thenReturn(List.of(user(77L, "Scene Owner")));

		this.mockMvc.perform(get("/api/users/77/scenes")
				.requestAttr(AuthenticatedUserRequest.USER_ID_ATTRIBUTE, 51L))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].sceneId").value(15L))
				.andExpect(jsonPath("$[0].ownerUserId").value(77L))
				.andExpect(jsonPath("$[0].creatorDisplayName").value("Scene Owner"))
				.andExpect(jsonPath("$[0].name").value("Aurora Drift"))
				.andExpect(jsonPath("$[0].description").value("Soft teal bloom with low-end drift."))
				.andExpect(jsonPath("$[0].sceneData.visualizer.shader").value("nebula"))
				.andExpect(jsonPath("$[0].createdAt").value("2026-03-26T15:30:00Z"))
				.andExpect(jsonPath("$[1].sceneId").value(16L))
				.andExpect(jsonPath("$[1].creatorDisplayName").value("Scene Owner"))
				.andExpect(jsonPath("$[1].name").value("Signal Bloom"));
	}

	@Test
	void scenesReturnsEmptyListWhenRequestedUserHasNoScenes() throws Exception {
		when(this.sceneService.getScenesForUser(51L, 77L))
				.thenReturn(List.of());

		this.mockMvc.perform(get("/api/users/77/scenes")
				.requestAttr(AuthenticatedUserRequest.USER_ID_ATTRIBUTE, 51L))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$").isEmpty());
	}

	@Test
	void scenesReturnsUnauthorizedWhenNoAuthenticatedRequestIdentityExists() throws Exception {
		when(this.sceneService.getScenesForUser(null, 77L))
				.thenThrow(new AuthenticationRequiredException("Authentication is required."));

		this.mockMvc.perform(get("/api/users/77/scenes"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
				.andExpect(jsonPath("$.message").value("Authentication is required."));
	}

	private Scene scene(Long sceneId, Long ownerUserId, String name, Instant createdAt) throws Exception {
		return scene(sceneId, ownerUserId, name, null, createdAt);
	}

	private Scene scene(Long sceneId, Long ownerUserId, String name, String description, Instant createdAt) throws Exception {
		Scene scene = new Scene(
				ownerUserId,
				name,
				description,
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						"""));
		ReflectionTestUtils.setField(scene, "id", sceneId);
		ReflectionTestUtils.setField(scene, "createdAt", createdAt);
		return scene;
	}

	private User user(Long userId, String displayName) {
		User user = new User("user-" + userId + "@example.com", "hashed-password", displayName, "", displayName);
		ReflectionTestUtils.setField(user, "id", userId);
		return user;
	}
}
