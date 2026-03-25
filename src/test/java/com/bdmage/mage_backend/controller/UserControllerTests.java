package com.bdmage.mage_backend.controller;

import java.time.Instant;

import com.bdmage.mage_backend.exception.ApiExceptionHandler;
import com.bdmage.mage_backend.exception.AuthenticationRequiredException;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.service.UserProfileService;
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

	private UserProfileService userProfileService;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		this.userProfileService = mock(UserProfileService.class);
		this.mockMvc = MockMvcBuilders
				.standaloneSetup(new UserController(this.userProfileService))
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
				.sessionAttr(AuthenticatedUserSession.USER_ID_ATTRIBUTE, 51L))
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
	void meReturnsUnauthorizedWhenNoAuthenticatedSessionExists() throws Exception {
		when(this.userProfileService.getAuthenticatedUser(null))
				.thenThrow(new AuthenticationRequiredException("Authentication is required."));

		this.mockMvc.perform(get("/users/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
				.andExpect(jsonPath("$.message").value("Authentication is required."));
	}
}
