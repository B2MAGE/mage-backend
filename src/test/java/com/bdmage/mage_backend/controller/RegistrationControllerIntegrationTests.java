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

		this.mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody(email, password, firstName, lastName, displayName)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value(email))
				.andExpect(jsonPath("$.firstName").value(firstName))
				.andExpect(jsonPath("$.lastName").value(lastName))
				.andExpect(jsonPath("$.displayName").value(displayName))
				.andExpect(jsonPath("$.authProvider").value("LOCAL"))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist());

		User savedUser = this.userRepository.findByEmail(email).orElseThrow();
		assertThat(savedUser.getFirstName()).isEqualTo(firstName);
		assertThat(savedUser.getLastName()).isEqualTo(lastName);
		assertThat(savedUser.getDisplayName()).isEqualTo(displayName);
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
				.content(requestBody(email, password, "Local", "User", "Local User")))
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
				.content(requestBody(email, password, "Local", "User", "Local User")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ACCOUNT_LINK_REQUIRED"))
				.andExpect(jsonPath("$.message").value(
						"A Google-backed account already exists for this email. Link local authentication through /api/auth/link/local after authenticating with Google."));
	}

	private static String requestBody(
			String email,
			String password,
			String firstName,
			String lastName,
			String displayName) {
		return "{\"email\":\"" + email
				+ "\",\"password\":\"" + password
				+ "\",\"firstName\":\"" + firstName
				+ "\",\"lastName\":\"" + lastName
				+ "\",\"displayName\":\"" + displayName
				+ "\"}";
	}
}
