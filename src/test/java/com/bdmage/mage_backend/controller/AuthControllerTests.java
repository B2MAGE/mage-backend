package com.bdmage.mage_backend.controller;

import com.bdmage.mage_backend.exception.AccountLinkRequiredException;
import com.bdmage.mage_backend.exception.ApiExceptionHandler;
import com.bdmage.mage_backend.exception.EmailAlreadyRegisteredException;
import com.bdmage.mage_backend.exception.InvalidCredentialsException;
import com.bdmage.mage_backend.exception.InvalidGoogleTokenException;
import com.bdmage.mage_backend.exception.InvalidLocalCredentialsException;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.service.AccountLinkingService;
import com.bdmage.mage_backend.service.AccountLinkingService.AccountLinkingResult;
import com.bdmage.mage_backend.service.AuthenticationTokenService;
import com.bdmage.mage_backend.service.GoogleAuthenticationService;
import com.bdmage.mage_backend.service.GoogleAuthenticationService.GoogleAuthenticationResult;
import com.bdmage.mage_backend.service.LoginService;
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

	private AccountLinkingService accountLinkingService;
	private AuthenticationTokenService authenticationTokenService;
	private GoogleAuthenticationService googleAuthenticationService;
	private LoginService loginService;
	private RegistrationService registrationService;
	private MockMvc mockMvc;
	private LocalValidatorFactoryBean validator;

	@BeforeEach
	void setUp() {
		this.accountLinkingService = mock(AccountLinkingService.class);
		this.authenticationTokenService = mock(AuthenticationTokenService.class);
		this.googleAuthenticationService = mock(GoogleAuthenticationService.class);
		this.loginService = mock(LoginService.class);
		this.registrationService = mock(RegistrationService.class);
		this.validator = new LocalValidatorFactoryBean();
		this.validator.afterPropertiesSet();
		this.mockMvc = MockMvcBuilders
				.standaloneSetup(new AuthController(
						this.accountLinkingService,
						this.authenticationTokenService,
						this.googleAuthenticationService,
						this.loginService,
						this.registrationService))
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
		when(this.authenticationTokenService.issueToken(googleUser)).thenReturn("issued-google-token");

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
				.andExpect(jsonPath("$.created").value(true))
				.andExpect(jsonPath("$.accessToken").value("issued-google-token"));
	}

	@Test
	void googleAuthenticationReturnsOkWhenServiceReusesAccount() throws Exception {
		User googleUser = User.google("user@example.com", "google-subject-2", "Google User");
		ReflectionTestUtils.setField(googleUser, "id", 22L);

		when(this.googleAuthenticationService.authenticate("repeat-token"))
				.thenReturn(new GoogleAuthenticationResult(googleUser, false));
		when(this.authenticationTokenService.issueToken(googleUser)).thenReturn("reissued-google-token");

		this.mockMvc.perform(post("/auth/google")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"idToken":"repeat-token"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.created").value(false))
				.andExpect(jsonPath("$.userId").value(22L))
				.andExpect(jsonPath("$.accessToken").value("reissued-google-token"));
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
				.thenThrow(new AccountLinkRequiredException(
						"A local account already exists for this email. Link Google through /auth/link/google after authenticating that local account."));

		this.mockMvc.perform(post("/auth/google")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"idToken":"conflict-token"}
						"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ACCOUNT_LINK_REQUIRED"));
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
						"Local authentication is already configured for this email."));

		this.mockMvc.perform(post("/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"existing@example.com","password":"secret-value","displayName":"Existing User"}
						"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"))
				.andExpect(jsonPath("$.message").value("Local authentication is already configured for this email."));
	}

	@Test
	void registrationReturnsConflictWhenExplicitLinkIsRequired() throws Exception {
		when(this.registrationService.register("existing@example.com", "secret-value", "Existing User"))
				.thenThrow(new AccountLinkRequiredException(
						"A Google-backed account already exists for this email. Link local authentication through /auth/link/local after authenticating with Google."));

		this.mockMvc.perform(post("/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"existing@example.com","password":"secret-value","displayName":"Existing User"}
						"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ACCOUNT_LINK_REQUIRED"));
	}

	@Test
	void loginReturnsLocalUserWhenCredentialsAreValid() throws Exception {
		User localUser = new User("local-user@example.com", "hashed-password", "Local User");
		ReflectionTestUtils.setField(localUser, "id", 41L);

		when(this.loginService.login("local-user@example.com", "secret-value"))
				.thenReturn(localUser);
		when(this.authenticationTokenService.issueToken(localUser)).thenReturn("issued-login-token");

		this.mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"local-user@example.com","password":"secret-value"}
						"""))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.userId").value(41L))
				.andExpect(jsonPath("$.email").value("local-user@example.com"))
				.andExpect(jsonPath("$.displayName").value("Local User"))
				.andExpect(jsonPath("$.authProvider").value("LOCAL"))
				.andExpect(jsonPath("$.accessToken").value("issued-login-token"))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist());
	}

	@Test
	void loginRejectsInvalidRequestBody() throws Exception {
		this.mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":" ","password":" "}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.details.email").value("email must not be blank"))
				.andExpect(jsonPath("$.details.password").value("password must not be blank"));
	}

	@Test
	void loginReturnsUnauthorizedWhenCredentialsAreInvalid() throws Exception {
		when(this.loginService.login("local-user@example.com", "wrong-password"))
				.thenThrow(new InvalidCredentialsException("Email or password is incorrect."));

		this.mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"local-user@example.com","password":"wrong-password"}
						"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
				.andExpect(jsonPath("$.message").value("Email or password is incorrect."));
	}

	@Test
	void linkGoogleReturnsLinkedUserWhenServiceLinksAccount() throws Exception {
		User linkedUser = User.localAndGoogle(
				"user@example.com",
				"hashed-password",
				"google-subject-1",
				"Linked User");
		ReflectionTestUtils.setField(linkedUser, "id", 51L);

		when(this.accountLinkingService.linkGoogle("user@example.com", "secret-value", "valid-token"))
				.thenReturn(new AccountLinkingResult(linkedUser, true));

		this.mockMvc.perform(post("/auth/link/google")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"user@example.com","password":"secret-value","idToken":"valid-token"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.userId").value(51L))
				.andExpect(jsonPath("$.authProvider").value("LOCAL_GOOGLE"))
				.andExpect(jsonPath("$.linked").value(true));
	}

	@Test
	void linkGoogleReturnsUnauthorizedWhenLocalCredentialsAreInvalid() throws Exception {
		when(this.accountLinkingService.linkGoogle("user@example.com", "secret-value", "valid-token"))
				.thenThrow(new InvalidLocalCredentialsException("Email or password is incorrect."));

		this.mockMvc.perform(post("/auth/link/google")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"user@example.com","password":"secret-value","idToken":"valid-token"}
						"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_LOCAL_CREDENTIALS"))
				.andExpect(jsonPath("$.message").value("Email or password is incorrect."));
	}

	@Test
	void linkLocalReturnsExistingLinkedUserWhenProviderIsAlreadyLinked() throws Exception {
		User linkedUser = User.localAndGoogle(
				"user@example.com",
				"hashed-password",
				"google-subject-2",
				"Linked User");
		ReflectionTestUtils.setField(linkedUser, "id", 52L);

		when(this.accountLinkingService.linkLocal("valid-token", "secret-value"))
				.thenReturn(new AccountLinkingResult(linkedUser, false));

		this.mockMvc.perform(post("/auth/link/local")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"idToken":"valid-token","password":"secret-value"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.userId").value(52L))
				.andExpect(jsonPath("$.authProvider").value("LOCAL_GOOGLE"))
				.andExpect(jsonPath("$.linked").value(false));
	}
}
