package com.bdmage.mage_backend.controller;

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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ProfileControllerIntegrationTests extends PostgresIntegrationTestSupport {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PasswordHashingService passwordHashingService;

	@Autowired
	private SceneRepository sceneRepository;

	@Autowired
	private UserRepository userRepository;

	@Test
	void publicProfileIsAnonymousCaseInsensitiveAndDoesNotExposePrivateAccountFields() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String handle = "public_" + uniqueSuffix;
		User profileOwner = new User(
				"private-" + uniqueSuffix + "@example.com",
				this.passwordHashingService.hash("password-" + uniqueSuffix),
				"Private",
				"Person",
				"Public Creator",
				handle);
		profileOwner.updateProfile(
				"Private",
				"Person",
				"Public Creator",
				handle,
				"Scenes that turn small sounds into large shapes.");
		profileOwner = this.userRepository.saveAndFlush(profileOwner);

		Scene scene = new Scene(
				profileOwner.getId(),
				"Soft Signal",
				"A patient field of light that wakes up with the beat.",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						"""));
		this.sceneRepository.saveAndFlush(scene);

		this.mockMvc.perform(get("/api/profiles/@" + handle.toUpperCase()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.userId").value(profileOwner.getId()))
				.andExpect(jsonPath("$.displayName").value("Public Creator"))
				.andExpect(jsonPath("$.handle").value(handle))
				.andExpect(jsonPath("$.description").value(
						"Scenes that turn small sounds into large shapes."))
				.andExpect(jsonPath("$.createdAt").isNotEmpty())
				.andExpect(jsonPath("$.scenes.length()").value(1))
				.andExpect(jsonPath("$.scenes[0].name").value("Soft Signal"))
				.andExpect(jsonPath("$.scenes[0].creatorDisplayName").value("Public Creator"))
				.andExpect(jsonPath("$.scenes[0].creatorHandle").value(handle))
				.andExpect(jsonPath("$.email").doesNotExist())
				.andExpect(jsonPath("$.firstName").doesNotExist())
				.andExpect(jsonPath("$.lastName").doesNotExist())
				.andExpect(jsonPath("$.authProvider").doesNotExist())
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist())
				.andExpect(jsonPath("$.googleSubject").doesNotExist());
	}

	@Test
	void publicProfileReturnsNotFoundForUnknownAndMalformedHandles() throws Exception {
		this.mockMvc.perform(get("/api/profiles/@missing_" + System.nanoTime()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PROFILE_NOT_FOUND"))
				.andExpect(jsonPath("$.message").value("Profile not found."));

		this.mockMvc.perform(get("/api/profiles/not-valid!"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PROFILE_NOT_FOUND"))
				.andExpect(jsonPath("$.message").value("Profile not found."));
	}
}
