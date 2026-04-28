package com.bdmage.mage_backend.controller;

import java.time.Instant;

import com.bdmage.mage_backend.model.PasswordResetToken;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.repository.PasswordResetTokenRepository;
import com.bdmage.mage_backend.repository.UserRepository;
import com.bdmage.mage_backend.service.PasswordHashingService;
import com.bdmage.mage_backend.service.PasswordResetService;
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
class PasswordResetControllerIntegrationTests extends PostgresIntegrationTestSupport {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordResetTokenRepository passwordResetTokenRepository;

	@Autowired
	private PasswordHashingService passwordHashingService;

	// --- POST /api/auth/reset-password/request ---

	@Test
	void requestReturnsOkWithTokenForRegisteredLocalUser() throws Exception {
		String suffix = String.valueOf(System.nanoTime());
		String email = "reset-local-" + suffix + "@example.com";

		this.userRepository.saveAndFlush(new User(email, this.passwordHashingService.hash("password"), "Reset User"));

		this.mockMvc.perform(post("/api/auth/reset-password/request")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").isNotEmpty())
				.andExpect(jsonPath("$.resetToken").isNotEmpty());
	}

	@Test
	void requestReturnsOkEvenWhenEmailNotRegistered() throws Exception {
		// Same 200 + same message — callers cannot tell whether the address exists
		this.mockMvc.perform(post("/api/auth/reset-password/request")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"nobody-" + System.nanoTime() + "@example.com\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").isNotEmpty())
				.andExpect(jsonPath("$.resetToken").doesNotExist());
	}

	@Test
	void requestReturnsOkForGoogleOnlyAccountWithoutIssuingToken() throws Exception {
		String suffix = String.valueOf(System.nanoTime());
		String email = "google-only-" + suffix + "@example.com";

		this.userRepository.saveAndFlush(User.google(email, "google-sub-" + suffix, "Google User"));

		this.mockMvc.perform(post("/api/auth/reset-password/request")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").isNotEmpty())
				.andExpect(jsonPath("$.resetToken").doesNotExist());
	}

	@Test
	void requestReturnsBadRequestForInvalidEmailFormat() throws Exception {
		this.mockMvc.perform(post("/api/auth/reset-password/request")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"not-an-email\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void requestReturnsBadRequestForBlankEmail() throws Exception {
		this.mockMvc.perform(post("/api/auth/reset-password/request")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	// --- POST /api/auth/reset-password/confirm ---

	@Test
	void confirmResetsPasswordWithValidToken() throws Exception {
		String suffix = String.valueOf(System.nanoTime());
		String email = "confirm-user-" + suffix + "@example.com";

		User user = this.userRepository.saveAndFlush(
				new User(email, this.passwordHashingService.hash("old-password"), "Confirm User"));

		String rawToken = extractResetToken(email);

		this.mockMvc.perform(post("/api/auth/reset-password/confirm")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"resetToken\":\"" + rawToken + "\",\"newPassword\":\"new-password\"}"))
				.andExpect(status().isOk());

		// Verify the password was actually changed in the database
		User updated = this.userRepository.findById(user.getId()).orElseThrow();
		assertThat(this.passwordHashingService.matches("new-password", updated.getPasswordHash())).isTrue();
		assertThat(this.passwordHashingService.matches("old-password", updated.getPasswordHash())).isFalse();
	}

	@Test
	void confirmReturnsBadRequestForUnknownToken() throws Exception {
		this.mockMvc.perform(post("/api/auth/reset-password/confirm")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"resetToken\":\"totally-made-up-token\",\"newPassword\":\"new-password\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_PASSWORD_RESET_TOKEN"));
	}

	@Test
	void confirmReturnsBadRequestForExpiredToken() throws Exception {
		String suffix = String.valueOf(System.nanoTime());
		String email = "expired-user-" + suffix + "@example.com";

		User user = this.userRepository.saveAndFlush(
				new User(email, this.passwordHashingService.hash("password"), "Expired User"));

		// Manually insert a token that already expired
		String rawToken = "expired-raw-token-" + suffix;
		String tokenHash = PasswordResetService.hashToken(rawToken);
		this.passwordResetTokenRepository.saveAndFlush(
				new PasswordResetToken(user.getId(), tokenHash, Instant.now().minusSeconds(60)));

		this.mockMvc.perform(post("/api/auth/reset-password/confirm")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"resetToken\":\"" + rawToken + "\",\"newPassword\":\"new-password\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_PASSWORD_RESET_TOKEN"));
	}

	@Test
	void confirmReturnsBadRequestForAlreadyUsedToken() throws Exception {
		String suffix = String.valueOf(System.nanoTime());
		String email = "used-token-user-" + suffix + "@example.com";

		this.userRepository.saveAndFlush(
				new User(email, this.passwordHashingService.hash("password"), "Used Token User"));

		String rawToken = extractResetToken(email);

		// First use — should succeed
		this.mockMvc.perform(post("/api/auth/reset-password/confirm")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"resetToken\":\"" + rawToken + "\",\"newPassword\":\"first-new-password\"}"))
				.andExpect(status().isOk());

		// Second use — token is spent
		this.mockMvc.perform(post("/api/auth/reset-password/confirm")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"resetToken\":\"" + rawToken + "\",\"newPassword\":\"second-new-password\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_PASSWORD_RESET_TOKEN"));
	}

	@Test
	void confirmReturnsBadRequestForBlankToken() throws Exception {
		this.mockMvc.perform(post("/api/auth/reset-password/confirm")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"resetToken\":\"\",\"newPassword\":\"new-password\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void confirmReturnsBadRequestForBlankNewPassword() throws Exception {
		this.mockMvc.perform(post("/api/auth/reset-password/confirm")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"resetToken\":\"some-token\",\"newPassword\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	// Hits the request endpoint and pulls the token out of the JSON response
	private String extractResetToken(String email) throws Exception {
		String body = this.mockMvc.perform(post("/api/auth/reset-password/request")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\"}"))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();

		int start = body.indexOf("\"resetToken\":\"") + "\"resetToken\":\"".length();
		int end = body.indexOf("\"", start);
		return body.substring(start, end);
	}

}
