package com.bdmage.mage_backend.controller;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Optional;

import com.bdmage.mage_backend.client.GoogleTokenVerifier;
import com.bdmage.mage_backend.client.GoogleTokenVerifier.VerifiedGoogleToken;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.repository.UserRepository;
import com.bdmage.mage_backend.service.PasswordHashingService;
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
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(UserControllerIntegrationTests.StubGoogleVerifierConfiguration.class)
class UserControllerIntegrationTests extends PostgresIntegrationTestSupport {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordHashingService passwordHashingService;

	@Test
	void meReturnsUnauthorizedWhenRequestHasNoAuthenticationHeader() throws Exception {
		this.mockMvc.perform(get("/users/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
				.andExpect(jsonPath("$.message").value("Authentication is required."));
	}

	@Test
	void meReturnsUnauthorizedWhenRequestUsesInvalidAuthenticationToken() throws Exception {
		this.mockMvc.perform(get("/users/me")
				.header("Authorization", "Bearer invalid-token"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_AUTH_TOKEN"))
				.andExpect(jsonPath("$.message").value("Authentication token is invalid."));
	}

	@Test
	void meReturnsProfileForTokenAuthenticatedLocalUser() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "profile-local-" + uniqueSuffix + "@example.com";
		String password = "password-" + uniqueSuffix;

		this.userRepository.saveAndFlush(new User(
				email,
				this.passwordHashingService.hash(password),
				"Profile Local User"));

		MvcResult loginResult = this.mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequestBody(email, password)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn();

		String accessToken = accessToken(loginResult);

		this.mockMvc.perform(get("/users/me")
				.header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(email))
				.andExpect(jsonPath("$.displayName").value("Profile Local User"))
				.andExpect(jsonPath("$.authProvider").value("LOCAL"))
				.andExpect(jsonPath("$.createdAt").isNotEmpty())
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist())
				.andExpect(jsonPath("$.googleSubject").doesNotExist());
	}

	@Test
	void meReturnsProfileForTokenAuthenticatedGoogleUser() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String subject = "google-profile-" + uniqueSuffix;
		String email = "profile-google-" + uniqueSuffix + "@example.com";

		MvcResult authenticationResult = this.mockMvc.perform(post("/auth/google")
				.contentType(MediaType.APPLICATION_JSON)
				.content(googleRequestBody(verifiedToken(subject, email, "Profile Google User"))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn();

		String accessToken = accessToken(authenticationResult);

		this.mockMvc.perform(get("/users/me")
				.header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(email))
				.andExpect(jsonPath("$.displayName").value("Profile Google User"))
				.andExpect(jsonPath("$.authProvider").value("GOOGLE"))
				.andExpect(jsonPath("$.createdAt").isNotEmpty())
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist())
				.andExpect(jsonPath("$.googleSubject").doesNotExist());
	}

	private static String loginRequestBody(String email, String password) {
		return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
	}

	private static String googleRequestBody(String idToken) {
		return "{\"idToken\":\"" + idToken + "\"}";
	}

	private static String verifiedToken(String subject, String email, String displayName) {
		return "verified|" + subject + "|" + email + "|" + displayName;
	}

	private static String accessToken(MvcResult result) throws Exception {
		Matcher matcher = Pattern.compile("\"accessToken\":\"([^\"]+)\"")
				.matcher(result.getResponse().getContentAsString());
		assertThat(matcher.find()).isTrue();
		return matcher.group(1);
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
