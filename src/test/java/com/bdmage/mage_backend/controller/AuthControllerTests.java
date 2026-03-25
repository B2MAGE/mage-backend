package com.bdmage.mage_backend.controller;

import com.bdmage.mage_backend.exception.ApiExceptionHandler;
import com.bdmage.mage_backend.exception.EmailAlreadyRegisteredException;
import com.bdmage.mage_backend.exception.GoogleAccountConflictException;
import com.bdmage.mage_backend.exception.InvalidGoogleTokenException;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.service.GoogleAuthenticationService;
import com.bdmage.mage_backend.service.GoogleAuthenticationService.GoogleAuthenticationResult;
import com.bdmage.mage_backend.service.RegistrationService;
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
	private RegistrationService registrationService;
	private MockMvc mockMvc;
	private LocalValidatorFactoryBean validator;

	@BeforeEach
	void setUp() {
		this.googleAuthenticationService = mock(GoogleAuthenticationService.class);
		this.registrationService = mock(RegistrationService.class);
		this.validator = new LocalValidatorFactoryBean();
		this.validator.afterPropertiesSet();
		this.mockMvc = MockMvcBuilders
				.standaloneSetup(new AuthController(this.googleAuthenticationService, this.registrationService))
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

	@Test
	void registrationReturnsCreatedLocalUser() throws Exception {
		User localUser = new User("new-user@example.com", "hashed-password", "New User");
		ReflectionTestUtils.setField(localUser, "id", 31L);

		when(this.registrationService.register("new-user@example.com", "secret-value", "New User"))
				.thenReturn(localUser);

		this.mockMvc.perform(post("/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"new-user@example.com","password":"secret-value","displayName":"New User"}
						"""))
				.andExpect(status().isCreated())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.userId").value(31L))
				.andExpect(jsonPath("$.email").value("new-user@example.com"))
				.andExpect(jsonPath("$.displayName").value("New User"))
				.andExpect(jsonPath("$.authProvider").value("LOCAL"))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist());
	}

	@Test
	void registrationRejectsInvalidRequestBody() throws Exception {
		this.mockMvc.perform(post("/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":" ","password":" ","displayName":" "}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.details.email").value("email must not be blank"))
				.andExpect(jsonPath("$.details.password").value("password must not be blank"))
				.andExpect(jsonPath("$.details.displayName").value("displayName must not be blank"));
	}

	@Test
	void registrationReturnsConflictWhenEmailAlreadyExists() throws Exception {
		when(this.registrationService.register("existing@example.com", "secret-value", "Existing User"))
				.thenThrow(new EmailAlreadyRegisteredException(
						"An account with this email address is already registered."));

		this.mockMvc.perform(post("/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"existing@example.com","password":"secret-value","displayName":"Existing User"}
						"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"))
				.andExpect(jsonPath("$.message").value("An account with this email address is already registered."));
	}
}
