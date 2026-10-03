package com.bdmage.mage_backend.controller;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Optional;

import com.bdmage.mage_backend.client.GoogleTokenVerifier;
import com.bdmage.mage_backend.client.GoogleTokenVerifier.VerifiedGoogleToken;
import com.bdmage.mage_backend.model.Scene;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.repository.SceneRepository;
import com.bdmage.mage_backend.repository.UserRepository;
import com.bdmage.mage_backend.service.PasswordHashingService;
import com.bdmage.mage_backend.support.PostgresIntegrationTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(UserControllerIntegrationTests.StubGoogleVerifierConfiguration.class)
class UserControllerIntegrationTests extends PostgresIntegrationTestSupport {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private SceneRepository sceneRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordHashingService passwordHashingService;

	@Test
	void meReturnsUnauthorizedWhenRequestHasNoAuthenticationHeader() throws Exception {
		this.mockMvc.perform(get("/api/users/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
				.andExpect(jsonPath("$.message").value("Authentication is required."));
	}

	@Test
	void meReturnsUnauthorizedWhenRequestUsesInvalidAuthenticationToken() throws Exception {
		this.mockMvc.perform(get("/api/users/me")
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
				"Profile",
				"Local",
				"Profile Local User",
				"profile_" + uniqueSuffix));

		MvcResult loginResult = this.mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequestBody(email, password)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn();

		String accessToken = accessToken(loginResult);

		this.mockMvc.perform(get("/api/users/me")
				.header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(email))
				.andExpect(jsonPath("$.firstName").value("Profile"))
				.andExpect(jsonPath("$.lastName").value("Local"))
				.andExpect(jsonPath("$.displayName").value("Profile Local User"))
				.andExpect(jsonPath("$.handle").value("profile_" + uniqueSuffix))
				.andExpect(jsonPath("$.description").isEmpty())
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

		MvcResult authenticationResult = this.mockMvc.perform(post("/api/auth/google")
				.contentType(MediaType.APPLICATION_JSON)
				.content(googleRequestBody(
						verifiedToken(subject, email, "Profile Google User"),
						"@google_" + uniqueSuffix)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn();

		String accessToken = accessToken(authenticationResult);

		this.mockMvc.perform(get("/api/users/me")
				.header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(email))
				.andExpect(jsonPath("$.firstName").value("Profile"))
				.andExpect(jsonPath("$.lastName").value("Google User"))
				.andExpect(jsonPath("$.displayName").value("Profile Google User"))
				.andExpect(jsonPath("$.handle").value("google_" + uniqueSuffix))
				.andExpect(jsonPath("$.authProvider").value("GOOGLE"))
				.andExpect(jsonPath("$.createdAt").isNotEmpty())
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist())
				.andExpect(jsonPath("$.googleSubject").doesNotExist());
	}

	@Test
	void updateMePersistsFirstAndLastNameForAuthenticatedUser() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "profile-update-" + uniqueSuffix + "@example.com";
		String password = "password-" + uniqueSuffix;

		this.userRepository.saveAndFlush(new User(
				email,
				this.passwordHashingService.hash(password),
				"Original",
				"Name",
				"Profile Local User",
				"original_" + uniqueSuffix));

		MvcResult loginResult = this.mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequestBody(email, password)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn();

		String accessToken = accessToken(loginResult);

		this.mockMvc.perform(put("/api/users/me")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"firstName":"Updated","lastName":"Profile","displayName":"Updated Profile","handle":"@Updated_%s","description":"  Scenes shaped by rhythm.  "}
						""".formatted(uniqueSuffix)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(email))
				.andExpect(jsonPath("$.firstName").value("Updated"))
				.andExpect(jsonPath("$.lastName").value("Profile"))
				.andExpect(jsonPath("$.displayName").value("Updated Profile"))
				.andExpect(jsonPath("$.handle").value("updated_" + uniqueSuffix))
				.andExpect(jsonPath("$.description").value("Scenes shaped by rhythm."));

		User savedUser = this.userRepository.findByEmail(email).orElseThrow();
		assertThat(savedUser.getFirstName()).isEqualTo("Updated");
		assertThat(savedUser.getLastName()).isEqualTo("Profile");
		assertThat(savedUser.getDisplayName()).isEqualTo("Updated Profile");
		assertThat(savedUser.getHandle()).isEqualTo("updated_" + uniqueSuffix);
		assertThat(savedUser.getDescription()).isEqualTo("Scenes shaped by rhythm.");
	}

	@Test
	void updateMeRejectsHandleAlreadyOwnedByAnotherUser() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "profile-handle-update-" + uniqueSuffix + "@example.com";
		String password = "password-" + uniqueSuffix;
		String takenHandle = "taken_" + uniqueSuffix;

		this.userRepository.saveAndFlush(new User(
				email,
				this.passwordHashingService.hash(password),
				"Profile",
				"User",
				"Profile User",
				"current_" + uniqueSuffix));
		this.userRepository.saveAndFlush(new User(
				"handle-owner-" + uniqueSuffix + "@example.com",
				this.passwordHashingService.hash("owner-password-" + uniqueSuffix),
				"Handle",
				"Owner",
				"Handle Owner",
				takenHandle));

		MvcResult loginResult = this.mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequestBody(email, password)))
				.andExpect(status().isOk())
				.andReturn();

		this.mockMvc.perform(put("/api/users/me")
				.header("Authorization", "Bearer " + accessToken(loginResult))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"firstName":"Profile","lastName":"User","displayName":"Profile User","handle":"@%s","description":null}
						""".formatted(takenHandle.toUpperCase())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("HANDLE_ALREADY_IN_USE"))
				.andExpect(jsonPath("$.details.handle").value("That handle is already in use."));
	}

	@Test
	void changePasswordUpdatesTheStoredHashForAuthenticatedLocalUser() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "password-change-" + uniqueSuffix + "@example.com";
		String currentPassword = "current-password-" + uniqueSuffix;
		String newPassword = "new-password-" + uniqueSuffix;

		this.userRepository.saveAndFlush(new User(
				email,
				this.passwordHashingService.hash(currentPassword),
				"Profile",
				"Local",
				"Profile Local User"));

		MvcResult loginResult = this.mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequestBody(email, currentPassword)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn();

		String accessToken = accessToken(loginResult);

		this.mockMvc.perform(put("/api/users/me/password")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"currentPassword":"%s","newPassword":"%s"}
						""".formatted(currentPassword, newPassword)))
				.andExpect(status().isNoContent());

		User savedUser = this.userRepository.findByEmail(email).orElseThrow();
		assertThat(this.passwordHashingService.matches(newPassword, savedUser.getPasswordHash())).isTrue();
		assertThat(this.passwordHashingService.matches(currentPassword, savedUser.getPasswordHash())).isFalse();
	}

	@Test
	void changePasswordRejectsInvalidCurrentPassword() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "password-invalid-" + uniqueSuffix + "@example.com";
		String currentPassword = "current-password-" + uniqueSuffix;

		this.userRepository.saveAndFlush(new User(
				email,
				this.passwordHashingService.hash(currentPassword),
				"Profile",
				"Local",
				"Profile Local User"));

		MvcResult loginResult = this.mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequestBody(email, currentPassword)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn();

		String accessToken = accessToken(loginResult);

		this.mockMvc.perform(put("/api/users/me/password")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"currentPassword":"wrong-password","newPassword":"new-password-value"}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"))
				.andExpect(jsonPath("$.message").value("Current password is incorrect."));
	}

	@Test
	void changePasswordRejectsGoogleOnlyAccounts() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String subject = "google-password-" + uniqueSuffix;
		String email = "google-password-" + uniqueSuffix + "@example.com";

		MvcResult authenticationResult = this.mockMvc.perform(post("/api/auth/google")
				.contentType(MediaType.APPLICATION_JSON)
				.content(googleRequestBody(
						verifiedToken(subject, email, "Profile Google User"),
						"@password_" + uniqueSuffix)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn();

		String accessToken = accessToken(authenticationResult);

		this.mockMvc.perform(put("/api/users/me/password")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"currentPassword":"unused-password","newPassword":"new-password-value"}
						"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("LOCAL_PASSWORD_CHANGE_UNAVAILABLE"))
				.andExpect(jsonPath("$.message").value("Local password changes are not available for this account."));
	}

	@Test
	void scenesReturnsUnauthorizedWhenRequestHasNoAuthenticationHeader() throws Exception {
		this.mockMvc.perform(get("/api/users/77/scenes"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
				.andExpect(jsonPath("$.message").value("Authentication is required."));
	}

	@Test
	void scenesReturnsRequestedUsersScenesForAuthenticatedRequest() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		User authenticatedUser = this.userRepository.saveAndFlush(new User(
				"scene-viewer-" + uniqueSuffix + "@example.com",
				this.passwordHashingService.hash("viewer-password-" + uniqueSuffix),
				"Scene Viewer"));
		User ownerUser = this.userRepository.saveAndFlush(new User(
				"scene-owner-" + uniqueSuffix + "@example.com",
				this.passwordHashingService.hash("owner-password-" + uniqueSuffix),
				"Scene Owner"));

		saveScene(ownerUser.getId(), "Aurora Drift", "Soft teal bloom with low-end drift.");
		saveScene(ownerUser.getId(), "Signal Bloom");

		MvcResult loginResult = this.mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequestBody(authenticatedUser.getEmail(), "viewer-password-" + uniqueSuffix)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn();

		String accessToken = accessToken(loginResult);

		this.mockMvc.perform(get("/api/users/" + ownerUser.getId() + "/scenes")
				.header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].ownerUserId").value(ownerUser.getId()))
				.andExpect(jsonPath("$[0].creatorDisplayName").value("Scene Owner"))
				.andExpect(jsonPath("$[0].name").value("Aurora Drift"))
				.andExpect(jsonPath("$[0].description").value("Soft teal bloom with low-end drift."))
				.andExpect(jsonPath("$[0].sceneData").doesNotExist())
				.andExpect(jsonPath("$[0].availability.code").value("SCENE_UPGRADE_REQUIRED"))
				.andExpect(jsonPath("$[0].createdAt").isNotEmpty())
				.andExpect(jsonPath("$[1].ownerUserId").value(ownerUser.getId()))
				.andExpect(jsonPath("$[1].creatorDisplayName").value("Scene Owner"))
				.andExpect(jsonPath("$[1].name").value("Signal Bloom"))
				.andExpect(jsonPath("$[1].createdAt").isNotEmpty());
	}

	@Test
	void scenesReturnsEmptyListWhenRequestedUserHasNoScenes() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		User authenticatedUser = this.userRepository.saveAndFlush(new User(
				"scene-reader-" + uniqueSuffix + "@example.com",
				this.passwordHashingService.hash("reader-password-" + uniqueSuffix),
				"Scene Reader"));
		User requestedUser = this.userRepository.saveAndFlush(new User(
				"scene-empty-" + uniqueSuffix + "@example.com",
				this.passwordHashingService.hash("empty-password-" + uniqueSuffix),
				"Scene Empty"));

		MvcResult loginResult = this.mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequestBody(authenticatedUser.getEmail(), "reader-password-" + uniqueSuffix)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn();

		String accessToken = accessToken(loginResult);

		this.mockMvc.perform(get("/api/users/" + requestedUser.getId() + "/scenes")
				.header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$").isEmpty());
	}

	private static String loginRequestBody(String email, String password) {
		return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
	}

	private static String googleRequestBody(String idToken, String handle) {
		return "{\"idToken\":\"" + idToken + "\",\"handle\":\"" + handle + "\"}";
	}

	private static String verifiedToken(String subject, String email, String displayName) {
		return "verified|" + subject + "|" + email + "|" + displayName;
	}

	private void saveScene(Long ownerUserId, String name) throws Exception {
		saveScene(ownerUserId, name, null);
	}

	private void saveScene(Long ownerUserId, String name, String description) throws Exception {
		Scene scene = new Scene(
				ownerUserId,
				name,
				description,
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						"""));
		this.sceneRepository.saveAndFlush(scene);
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
