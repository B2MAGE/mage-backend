package com.bdmage.mage_backend.controller;

import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.repository.UserRepository;
import com.bdmage.mage_backend.service.PasswordHashingService;
import com.bdmage.mage_backend.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
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
@Import(GoogleAuthControllerIntegrationTests.StubGoogleVerifierConfiguration.class)
class AccountLinkingControllerIntegrationTests extends PostgresIntegrationTestSupport {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordHashingService passwordHashingService;

	@Test
	void linkGoogleLinksExistingLocalAccountToGoogle() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "local-link-" + uniqueSuffix + "@example.com";
		String password = "password-" + uniqueSuffix;
		String googleSubject = "google-subject-" + uniqueSuffix;

		this.userRepository.saveAndFlush(new User(email, this.passwordHashingService.hash(password), "Local User"));

		this.mockMvc.perform(post("/api/auth/link/google")
				.contentType(MediaType.APPLICATION_JSON)
				.content(googleLinkRequestBody(email, password, verifiedToken(googleSubject, email, "Google User"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(email))
				.andExpect(jsonPath("$.authProvider").value("LOCAL_GOOGLE"))
				.andExpect(jsonPath("$.linked").value(true));

		User savedUser = this.userRepository.findByEmail(email).orElseThrow();
		assertThat(savedUser.getGoogleSubject()).isEqualTo(googleSubject);
		assertThat(this.passwordHashingService.matches(password, savedUser.getPasswordHash())).isTrue();
	}

	@Test
	void linkLocalLinksExistingGoogleAccountToLocalAuthentication() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "google-link-" + uniqueSuffix + "@example.com";
		String password = "password-" + uniqueSuffix;
		String googleSubject = "google-subject-" + uniqueSuffix;

		this.userRepository.saveAndFlush(User.google(email, googleSubject, "Google User"));

		this.mockMvc.perform(post("/api/auth/link/local")
				.contentType(MediaType.APPLICATION_JSON)
				.content(localLinkRequestBody(verifiedToken(googleSubject, email, "Google User"), password)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(email))
				.andExpect(jsonPath("$.authProvider").value("LOCAL_GOOGLE"))
				.andExpect(jsonPath("$.linked").value(true));

		User savedUser = this.userRepository.findByEmail(email).orElseThrow();
		assertThat(savedUser.getGoogleSubject()).isEqualTo(googleSubject);
		assertThat(this.passwordHashingService.matches(password, savedUser.getPasswordHash())).isTrue();
	}

	@Test
	void linkGoogleRejectsMismatchedGoogleEmail() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "mismatch-link-" + uniqueSuffix + "@example.com";
		String password = "password-" + uniqueSuffix;

		this.userRepository.saveAndFlush(new User(email, this.passwordHashingService.hash(password), "Local User"));

		this.mockMvc.perform(post("/api/auth/link/google")
				.contentType(MediaType.APPLICATION_JSON)
				.content(googleLinkRequestBody(
						email,
						password,
						verifiedToken("google-subject-" + uniqueSuffix, "other-" + email, "Google User"))))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ACCOUNT_CONFLICT"))
				.andExpect(jsonPath("$.message").value(
						"The Google account email must match the local account email before linking."));
	}

	private static String verifiedToken(String subject, String email, String displayName) {
		return "verified|" + subject + "|" + email + "|" + displayName;
	}

	private static String googleLinkRequestBody(String email, String password, String idToken) {
		return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"idToken\":\"" + idToken + "\"}";
	}

	private static String localLinkRequestBody(String idToken, String password) {
		return "{\"idToken\":\"" + idToken + "\",\"password\":\"" + password + "\"}";
	}
}

