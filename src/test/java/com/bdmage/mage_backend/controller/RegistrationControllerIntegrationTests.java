package com.bdmage.mage_backend.controller;

import com.bdmage.mage_backend.model.User;
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
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RegistrationControllerIntegrationTests extends PostgresIntegrationTestSupport {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordHashingService passwordHashingService;

	@Test
	void registrationCreatesLocalUserWithHashedPassword() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "local-user-" + uniqueSuffix + "@example.com";
		String password = "password-" + uniqueSuffix;
		String firstName = "Local";
		String lastName = "User";
		String displayName = "Local User";
		String handle = "@local_" + uniqueSuffix;

		this.mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody(email, password, firstName, lastName, displayName, handle)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value(email))
				.andExpect(jsonPath("$.firstName").value(firstName))
				.andExpect(jsonPath("$.lastName").value(lastName))
				.andExpect(jsonPath("$.displayName").value(displayName))
				.andExpect(jsonPath("$.handle").value(handle.substring(1)))
				.andExpect(jsonPath("$.authProvider").value("LOCAL"))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist());

		User savedUser = this.userRepository.findByEmail(email).orElseThrow();
		assertThat(savedUser.getFirstName()).isEqualTo(firstName);
		assertThat(savedUser.getLastName()).isEqualTo(lastName);
		assertThat(savedUser.getDisplayName()).isEqualTo(displayName);
		assertThat(savedUser.getHandle()).isEqualTo(handle.substring(1));
		assertThat(savedUser.getPasswordHash()).isNotEqualTo(password);
		assertThat(this.passwordHashingService.matches(password, savedUser.getPasswordHash())).isTrue();
	}

	@Test
	void registrationReturnsConflictWhenLocalAuthenticationIsAlreadyConfigured() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "registered-local-user-" + uniqueSuffix + "@example.com";
		String password = "password-" + uniqueSuffix;

		this.userRepository.saveAndFlush(new User(email, "hashed-password-value", "Local User"));

		this.mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody(email, password, "Local", "User", "Local User", "@local_" + uniqueSuffix)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"))
				.andExpect(jsonPath("$.message").value("Local authentication is already configured for this email."));
	}

	@Test
	void registrationReturnsConflictWhenEmailBelongsToGoogleAccount() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "registered-user-" + uniqueSuffix + "@example.com";
		String password = "password-" + uniqueSuffix;

		this.userRepository.saveAndFlush(User.google(email, "google-subject-" + uniqueSuffix, "Google User"));

		this.mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody(email, password, "Local", "User", "Local User", "@google_" + uniqueSuffix)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ACCOUNT_LINK_REQUIRED"))
				.andExpect(jsonPath("$.message").value(
						"A Google-backed account already exists for this email. Link local authentication through /api/auth/link/local after authenticating with Google."));
	}

	@Test
	void registrationRejectsCaseInsensitiveDuplicateHandle() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String handle = "shared_" + uniqueSuffix;
		this.userRepository.saveAndFlush(new User(
				"handle-owner-" + uniqueSuffix + "@example.com",
				"hashed-password-value",
				"Handle",
				"Owner",
				"Handle Owner",
				handle));

		this.mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody(
						"handle-new-" + uniqueSuffix + "@example.com",
						"password-" + uniqueSuffix,
						"New",
						"User",
						"New User",
						"@" + handle.toUpperCase())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("HANDLE_ALREADY_IN_USE"))
				.andExpect(jsonPath("$.details.handle").value("That handle is already in use."));
	}

	@Test
	void registrationRejectsMissingOrMalformedHandle() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "invalid-handle-" + uniqueSuffix + "@example.com";

		this.mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody(email, "password-" + uniqueSuffix, "New", "User", "New User", "")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.details.handle").value("handle must not be blank"));

		this.mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody(email, "password-" + uniqueSuffix, "New", "User", "New User", "no-at-sign")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.details.handle").value(
						"handle must start with @ and contain 3 to 30 letters, numbers, or underscores"));
	}

	private static String requestBody(
			String email,
			String password,
			String firstName,
			String lastName,
			String displayName,
			String handle) {
		return "{\"email\":\"" + email
				+ "\",\"password\":\"" + password
				+ "\",\"firstName\":\"" + firstName
				+ "\",\"lastName\":\"" + lastName
				+ "\",\"displayName\":\"" + displayName
				+ "\",\"handle\":\"" + handle
				+ "\"}";
	}
}
