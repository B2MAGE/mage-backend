package com.bdmage.mage_backend.controller;

import java.util.Optional;

import com.bdmage.mage_backend.client.GoogleTokenVerifier;
import com.bdmage.mage_backend.client.GoogleTokenVerifier.VerifiedGoogleToken;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.repository.UserRepository;
import com.bdmage.mage_backend.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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
class GoogleAuthControllerIntegrationTests extends PostgresIntegrationTestSupport {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Test
	void googleAuthenticationCreatesAndThenReusesTheSameGoogleAccount() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String subject = "google-subject-" + uniqueSuffix;
		String email = "google-user-" + uniqueSuffix + "@example.com";
		String token = verifiedToken(subject, email, "Google User");
		long countBefore = this.userRepository.count();

		this.mockMvc.perform(post("/auth/google")
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody(token)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value(email))
				.andExpect(jsonPath("$.authProvider").value("GOOGLE"))
				.andExpect(jsonPath("$.created").value(true));

		User savedUser = this.userRepository.findByGoogleSubject(subject).orElseThrow();

		this.mockMvc.perform(post("/auth/google")
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.userId").value(savedUser.getId()))
				.andExpect(jsonPath("$.created").value(false));

		assertThat(this.userRepository.count()).isEqualTo(countBefore + 1);
	}

	@Test
	void googleAuthenticationReturnsConflictWhenEmailBelongsToLocalAccount() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String subject = "google-subject-" + uniqueSuffix;
		String email = "local-conflict-" + uniqueSuffix + "@example.com";
		this.userRepository.saveAndFlush(new User(email, "hashed-password-value", "Local User"));

		this.mockMvc.perform(post("/auth/google")
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody(verifiedToken(subject, email, "Google User"))))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ACCOUNT_CONFLICT"))
				.andExpect(jsonPath("$.message").value(
						"A local account already exists for this email. Account linking is not available yet."));

		assertThat(this.userRepository.findByGoogleSubject(subject)).isEmpty();
	}

	@Test
	void googleAuthenticationRejectsInvalidTokens() throws Exception {
		this.mockMvc.perform(post("/auth/google")
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody("invalid-token")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_GOOGLE_TOKEN"));
	}

	private static String verifiedToken(String subject, String email, String displayName) {
		return "verified|" + subject + "|" + email + "|" + displayName;
	}

	private static String requestBody(String idToken) {
		return "{\"idToken\":\"" + idToken + "\"}";
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class StubGoogleVerifierConfiguration {

		@Bean
		@Primary
		GoogleTokenVerifier googleTokenVerifier() {
			return idToken -> {
				if (idToken == null || idToken.isBlank()) {
					return Optional.empty();
				}

				String[] parts = idToken.split("\\|", 4);
				if (parts.length != 4 || !"verified".equals(parts[0])) {
					return Optional.empty();
				}

				return Optional.of(new VerifiedGoogleToken(parts[1], parts[2], true, parts[3]));
			};
		}
	}
}
