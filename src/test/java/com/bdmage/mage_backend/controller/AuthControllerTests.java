package com.bdmage.mage_backend.controller;

import com.bdmage.mage_backend.exception.ApiExceptionHandler;
import com.bdmage.mage_backend.exception.GoogleAccountConflictException;
import com.bdmage.mage_backend.exception.InvalidGoogleTokenException;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.service.GoogleAuthenticationService;
import com.bdmage.mage_backend.service.GoogleAuthenticationService.GoogleAuthenticationResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTests {

	private GoogleAuthenticationService googleAuthenticationService;
	private MockMvc mockMvc;
	private LocalValidatorFactoryBean validator;

	@BeforeEach
	void setUp() {
		this.googleAuthenticationService = mock(GoogleAuthenticationService.class);
		this.validator = new LocalValidatorFactoryBean();
		this.validator.afterPropertiesSet();
		this.mockMvc = MockMvcBuilders
				.standaloneSetup(new AuthController(this.googleAuthenticationService))
				.setControllerAdvice(new ApiExceptionHandler())
				.setValidator(this.validator)
				.build();
	}

	@AfterEach
	void tearDown() {
		this.validator.close();
	}

	@Test
	void googleAuthenticationReturnsCreatedUserWhenServiceCreatesAccount() throws Exception {
		User googleUser = User.google("user@example.com", "google-subject-1", "Google User");
		ReflectionTestUtils.setField(googleUser, "id", 21L);

		when(this.googleAuthenticationService.authenticate("valid-token"))
				.thenReturn(new GoogleAuthenticationResult(googleUser, true));

		this.mockMvc.perform(post("/auth/google")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"idToken":"valid-token"}
						"""))
				.andExpect(status().isCreated())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.userId").value(21L))
				.andExpect(jsonPath("$.email").value("user@example.com"))
				.andExpect(jsonPath("$.displayName").value("Google User"))
				.andExpect(jsonPath("$.authProvider").value("GOOGLE"))
				.andExpect(jsonPath("$.created").value(true));
	}

	@Test
	void googleAuthenticationReturnsOkWhenServiceReusesAccount() throws Exception {
		User googleUser = User.google("user@example.com", "google-subject-2", "Google User");
		ReflectionTestUtils.setField(googleUser, "id", 22L);

		when(this.googleAuthenticationService.authenticate("repeat-token"))
				.thenReturn(new GoogleAuthenticationResult(googleUser, false));

		this.mockMvc.perform(post("/auth/google")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"idToken":"repeat-token"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.created").value(false))
				.andExpect(jsonPath("$.userId").value(22L));
	}

	@Test
	void googleAuthenticationRejectsBlankTokenRequest() throws Exception {
		this.mockMvc.perform(post("/auth/google")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"idToken":" "}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.details.idToken").value("idToken must not be blank"));
	}

	@Test
	void googleAuthenticationReturnsUnauthorizedWhenTokenIsInvalid() throws Exception {
		when(this.googleAuthenticationService.authenticate("invalid-token"))
				.thenThrow(new InvalidGoogleTokenException("Google ID token is invalid or expired."));

		this.mockMvc.perform(post("/auth/google")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"idToken":"invalid-token"}
						"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_GOOGLE_TOKEN"))
				.andExpect(jsonPath("$.message").value("Google ID token is invalid or expired."));
	}

	@Test
	void googleAuthenticationReturnsConflictWhenAccountRulesConflict() throws Exception {
		when(this.googleAuthenticationService.authenticate("conflict-token"))
				.thenThrow(new GoogleAccountConflictException(
						"A local account already exists for this email. Account linking is not available yet."));

		this.mockMvc.perform(post("/auth/google")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"idToken":"conflict-token"}
						"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ACCOUNT_CONFLICT"));
	}
}
