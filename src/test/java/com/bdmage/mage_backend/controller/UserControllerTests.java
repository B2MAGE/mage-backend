package com.bdmage.mage_backend.controller;

import java.time.Instant;
import java.util.List;

import com.bdmage.mage_backend.config.AuthenticatedUserRequest;
import com.bdmage.mage_backend.exception.ApiExceptionHandler;
import com.bdmage.mage_backend.exception.AuthenticationRequiredException;
import com.bdmage.mage_backend.model.Preset;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.service.PresetService;
import com.bdmage.mage_backend.service.UserProfileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerTests {

	private final ObjectMapper objectMapper = new ObjectMapper();

	private PresetService presetService;
	private UserProfileService userProfileService;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		this.presetService = mock(PresetService.class);
		this.userProfileService = mock(UserProfileService.class);
		this.mockMvc = MockMvcBuilders
				.standaloneSetup(new UserController(this.presetService, this.userProfileService))
				.setControllerAdvice(new ApiExceptionHandler())
				.build();
	}

	@Test
	void meReturnsAuthenticatedUserProfileWithoutSensitiveFields() throws Exception {
		User user = new User("profile-user@example.com", "hashed-password", "Profile User");
		ReflectionTestUtils.setField(user, "id", 51L);
		ReflectionTestUtils.setField(user, "createdAt", Instant.parse("2026-03-25T20:15:30Z"));

		when(this.userProfileService.getAuthenticatedUser(51L)).thenReturn(user);

		this.mockMvc.perform(get("/users/me")
				.requestAttr(AuthenticatedUserRequest.USER_ID_ATTRIBUTE, 51L))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.userId").value(51L))
				.andExpect(jsonPath("$.email").value("profile-user@example.com"))
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

		this.mockMvc.perform(get("/users/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
				.andExpect(jsonPath("$.message").value("Authentication is required."));
	}

	@Test
	void presetsReturnsRequestedUsersPresets() throws Exception {
		Preset firstPreset = preset(15L, 77L, "Aurora Drift", Instant.parse("2026-03-26T15:30:00Z"));
		Preset secondPreset = preset(16L, 77L, "Signal Bloom", Instant.parse("2026-03-26T16:45:00Z"));

		when(this.presetService.getPresetsForUser(51L, 77L))
				.thenReturn(List.of(firstPreset, secondPreset));

		this.mockMvc.perform(get("/users/77/presets")
				.requestAttr(AuthenticatedUserRequest.USER_ID_ATTRIBUTE, 51L))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(15L))
				.andExpect(jsonPath("$[0].name").value("Aurora Drift"))
				.andExpect(jsonPath("$[0].presetId").doesNotExist())
				.andExpect(jsonPath("$[0].ownerUserId").doesNotExist())
				.andExpect(jsonPath("$[0].sceneData").doesNotExist())
				.andExpect(jsonPath("$[0].createdAt").doesNotExist())
				.andExpect(jsonPath("$[1].id").value(16L))
				.andExpect(jsonPath("$[1].name").value("Signal Bloom"));
	}

	@Test
	void presetsReturnsEmptyListWhenRequestedUserHasNoPresets() throws Exception {
		when(this.presetService.getPresetsForUser(51L, 77L))
				.thenReturn(List.of());

		this.mockMvc.perform(get("/users/77/presets")
				.requestAttr(AuthenticatedUserRequest.USER_ID_ATTRIBUTE, 51L))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$").isEmpty());
	}

	@Test
	void presetsReturnsUnauthorizedWhenNoAuthenticatedRequestIdentityExists() throws Exception {
		when(this.presetService.getPresetsForUser(null, 77L))
				.thenThrow(new AuthenticationRequiredException("Authentication is required."));

		this.mockMvc.perform(get("/users/77/presets"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
				.andExpect(jsonPath("$.message").value("Authentication is required."));
	}

	private Preset preset(Long presetId, Long ownerUserId, String name, Instant createdAt) throws Exception {
		Preset preset = new Preset(
				ownerUserId,
				name,
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						"""));
		ReflectionTestUtils.setField(preset, "id", presetId);
		ReflectionTestUtils.setField(preset, "createdAt", createdAt);
		return preset;
	}
}
