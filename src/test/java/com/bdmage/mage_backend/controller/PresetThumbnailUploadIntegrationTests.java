package com.bdmage.mage_backend.controller;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.bdmage.mage_backend.model.Preset;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.repository.PresetRepository;
import com.bdmage.mage_backend.repository.UserRepository;
import com.bdmage.mage_backend.service.PasswordHashingService;
import com.bdmage.mage_backend.service.ThumbnailStorageService;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
// Swap in a fake storage service so these tests don't write files to disk
@Import(PresetThumbnailUploadIntegrationTests.StubThumbnailStorageConfiguration.class)
class PresetThumbnailUploadIntegrationTests extends PostgresIntegrationTestSupport {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PresetRepository presetRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordHashingService passwordHashingService;

	// --- Tests ---

	@Test
	void uploadThumbnailReturnsUnauthorizedWhenRequestHasNoAuthenticationHeader() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "thumb.png", "image/png", new byte[]{1, 2, 3});

		this.mockMvc.perform(multipart("/presets/15/thumbnail").file(file))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
				.andExpect(jsonPath("$.message").value("Authentication is required."));
	}

	@Test
	void uploadThumbnailUpdatesPresetRefForOwner() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "thumb-owner-" + uniqueSuffix + "@example.com";
		String password = "password-" + uniqueSuffix;

		User owner = this.userRepository.saveAndFlush(new User(
				email,
				this.passwordHashingService.hash(password),
				"Thumb Owner"));

		Preset preset = this.presetRepository.saveAndFlush(new Preset(
				owner.getId(),
				"Aurora Drift",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						""")));

		String accessToken = accessToken(this.mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequestBody(email, password)))
				.andExpect(status().isOk())
				.andReturn());

		MockMultipartFile file = new MockMultipartFile("file", "thumb.png", "image/png", new byte[]{1, 2, 3});

		this.mockMvc.perform(multipart("/presets/" + preset.getId() + "/thumbnail")
				.file(file)
				.header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.presetId").value(preset.getId()))
				// The stub storage service returns "thumbnails/{id}/stub-thumb.png"
				.andExpect(jsonPath("$.thumbnailRef").value("thumbnails/" + preset.getId() + "/stub-thumb.png"));

		// Double-check the DB row was actually updated
		Preset refreshed = this.presetRepository.findById(preset.getId()).orElseThrow();
		assertThat(refreshed.getThumbnailRef()).isEqualTo("thumbnails/" + preset.getId() + "/stub-thumb.png");
	}

	@Test
	void uploadThumbnailReturnsForbiddenWhenCallerIsNotOwner() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());

		// Two separate users — only the owner should be allowed to upload
		User owner = this.userRepository.saveAndFlush(new User(
				"thumb-owner2-" + uniqueSuffix + "@example.com",
				this.passwordHashingService.hash("ownerpass"),
				"Real Owner"));

		String otherEmail = "thumb-other-" + uniqueSuffix + "@example.com";
		String otherPassword = "otherpass-" + uniqueSuffix;
		this.userRepository.saveAndFlush(new User(
				otherEmail,
				this.passwordHashingService.hash(otherPassword),
				"Other User"));

		Preset preset = this.presetRepository.saveAndFlush(new Preset(
				owner.getId(),
				"Owned Preset",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"pulse"}}
						""")));

		// Log in as the non-owner
		String otherAccessToken = accessToken(this.mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequestBody(otherEmail, otherPassword)))
				.andExpect(status().isOk())
				.andReturn());

		MockMultipartFile file = new MockMultipartFile("file", "thumb.png", "image/png", new byte[]{1, 2, 3});

		this.mockMvc.perform(multipart("/presets/" + preset.getId() + "/thumbnail")
				.file(file)
				.header("Authorization", "Bearer " + otherAccessToken))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("PRESET_OWNERSHIP_REQUIRED"))
				.andExpect(jsonPath("$.message").value("Preset ownership is required."));
	}

	@Test
	void uploadThumbnailReturnsNotFoundForNonexistentPreset() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "thumb-missing-" + uniqueSuffix + "@example.com";
		String password = "password-" + uniqueSuffix;

		this.userRepository.saveAndFlush(new User(
				email,
				this.passwordHashingService.hash(password),
				"Missing Preset User"));

		String accessToken = accessToken(this.mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequestBody(email, password)))
				.andExpect(status().isOk())
				.andReturn());

		MockMultipartFile file = new MockMultipartFile("file", "thumb.png", "image/png", new byte[]{1, 2, 3});

		this.mockMvc.perform(multipart("/presets/99999/thumbnail")
				.file(file)
				.header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PRESET_NOT_FOUND"))
				.andExpect(jsonPath("$.message").value("Preset not found."));
	}

	@Test
	void uploadThumbnailReturnsBadRequestForInvalidContentType() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "thumb-invalid-" + uniqueSuffix + "@example.com";
		String password = "password-" + uniqueSuffix;

		User owner = this.userRepository.saveAndFlush(new User(
				email,
				this.passwordHashingService.hash(password),
				"Invalid Upload User"));

		Preset preset = this.presetRepository.saveAndFlush(new Preset(
				owner.getId(),
				"Aurora Drift",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						""")));

		String accessToken = accessToken(this.mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequestBody(email, password)))
				.andExpect(status().isOk())
				.andReturn());

		// PDFs are not valid thumbnails — should be rejected before storage is ever called
		MockMultipartFile pdfFile = new MockMultipartFile("file", "doc.pdf", "application/pdf", new byte[]{1, 2, 3});

		this.mockMvc.perform(multipart("/presets/" + preset.getId() + "/thumbnail")
				.file(pdfFile)
				.header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_THUMBNAIL"))
				.andExpect(jsonPath("$.message").value("Thumbnail must be a valid image (jpeg, png, webp, or gif)."));
	}

	// --- Stub configuration ---

	/**
	 * Replaces the real LocalThumbnailStorageService with a simple lambda that
	 * returns a predictable ref string. Keeps tests fast and avoids touching disk.
	 */
	@TestConfiguration
	static class StubThumbnailStorageConfiguration {

		@Bean
		@Primary
		ThumbnailStorageService stubThumbnailStorageService() {
			// Just echo back a stable path so tests can assert on it
			return (file, presetId) -> "thumbnails/" + presetId + "/stub-thumb.png";
		}
	}

	// --- Helpers (same pattern used by the other integration test classes) ---

	private static String loginRequestBody(String email, String password) {
		return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
	}

	private static String accessToken(MvcResult result) throws Exception {
		Matcher matcher = Pattern.compile("\"accessToken\":\"([^\"]+)\"")
				.matcher(result.getResponse().getContentAsString());
		assertThat(matcher.find()).isTrue();
		return matcher.group(1);
	}
}
