package com.bdmage.mage_backend.controller;

import java.time.Instant;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
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

	@Test
	void createPresetThumbnailUploadReturnsUnauthorizedWhenRequestHasNoAuthenticationHeader() throws Exception {
		this.mockMvc.perform(post("/api/presets/thumbnail/presign")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "filename":"thumb.png",
						  "contentType":"image/png",
						  "sizeBytes":3
						}
						"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
				.andExpect(jsonPath("$.message").value("Authentication is required."));
	}

	@Test
	void createThumbnailUploadReturnsUnauthorizedWhenRequestHasNoAuthenticationHeader() throws Exception {
		this.mockMvc.perform(post("/api/presets/15/thumbnail/presign")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "filename":"thumb.png",
						  "contentType":"image/png",
						  "sizeBytes":3
						}
						"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
				.andExpect(jsonPath("$.message").value("Authentication is required."));
	}

	@Test
	void createPresetThumbnailUploadReturnsPresignedPayloadForAuthenticatedUser() throws Exception {
		User owner = createUser("new-thumb-owner");
		String accessToken = accessToken(login(owner.getEmail(), ownerPassword(owner.getEmail())));

		this.mockMvc.perform(post("/api/presets/thumbnail/presign")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "filename":"thumb.png",
						  "contentType":"image/png",
						  "sizeBytes":3
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.objectKey").value("presets/pending/" + owner.getId() + "/thumbnails/stub-thumb.png"))
				.andExpect(jsonPath("$.method").value("PUT"))
				.andExpect(jsonPath("$.headers.Content-Type").value("image/png"));
	}

	@Test
	void createThumbnailUploadReturnsPresignedPayloadForOwner() throws Exception {
		User owner = createUser("thumb-owner");
		Preset preset = this.presetRepository.saveAndFlush(new Preset(
				owner.getId(),
				"Aurora Drift",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						""")));

		String accessToken = accessToken(login(owner.getEmail(), ownerPassword(owner.getEmail())));

		this.mockMvc.perform(post("/api/presets/" + preset.getId() + "/thumbnail/presign")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "filename":"thumb.png",
						  "contentType":"image/png",
						  "sizeBytes":3
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.objectKey").value("presets/" + preset.getId() + "/thumbnails/stub-thumb.png"))
				.andExpect(jsonPath("$.method").value("PUT"))
				.andExpect(jsonPath("$.headers.Content-Type").value("image/png"));
	}

	@Test
	void createThumbnailUploadReturnsForbiddenWhenCallerIsNotOwner() throws Exception {
		User owner = createUser("thumb-owner2");
		User other = createUser("thumb-other");
		Preset preset = this.presetRepository.saveAndFlush(new Preset(
				owner.getId(),
				"Owned Preset",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"pulse"}}
						""")));

		String otherAccessToken = accessToken(login(other.getEmail(), ownerPassword(other.getEmail())));

		this.mockMvc.perform(post("/api/presets/" + preset.getId() + "/thumbnail/presign")
				.header("Authorization", "Bearer " + otherAccessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "filename":"thumb.png",
						  "contentType":"image/png",
						  "sizeBytes":3
						}
						"""))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("PRESET_OWNERSHIP_REQUIRED"))
				.andExpect(jsonPath("$.message").value("Preset ownership is required."));
	}

	@Test
	void createThumbnailUploadReturnsNotFoundForNonexistentPreset() throws Exception {
		User user = createUser("thumb-missing");
		String accessToken = accessToken(login(user.getEmail(), ownerPassword(user.getEmail())));

		this.mockMvc.perform(post("/api/presets/99999/thumbnail/presign")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "filename":"thumb.png",
						  "contentType":"image/png",
						  "sizeBytes":3
						}
						"""))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PRESET_NOT_FOUND"))
				.andExpect(jsonPath("$.message").value("Preset not found."));
	}

	@Test
	void createThumbnailUploadReturnsBadRequestForInvalidContentType() throws Exception {
		User owner = createUser("thumb-invalid");
		Preset preset = this.presetRepository.saveAndFlush(new Preset(
				owner.getId(),
				"Aurora Drift",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						""")));
		String accessToken = accessToken(login(owner.getEmail(), ownerPassword(owner.getEmail())));

		this.mockMvc.perform(post("/api/presets/" + preset.getId() + "/thumbnail/presign")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "filename":"doc.pdf",
						  "contentType":"application/pdf",
						  "sizeBytes":3
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_THUMBNAIL"))
				.andExpect(jsonPath("$.message").value("Thumbnail must be a valid image (jpeg, png, webp, or gif)."));
	}

	@Test
	void createPresetWithThumbnailObjectKeyPersistsThumbnailRefOnlyAfterFinalize() throws Exception {
		User owner = createUser("thumb-create");
		String accessToken = accessToken(login(owner.getEmail(), ownerPassword(owner.getEmail())));

		MvcResult createResult = this.mockMvc.perform(post("/api/presets")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "name":"Aurora Drift",
						  "sceneData":{"visualizer":{"shader":"nebula"}},
						  "thumbnailObjectKey":"presets/pending/%d/thumbnails/stub-thumb.png"
						}
						""".formatted(owner.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.thumbnailRef").value(
						"https://cdn.test.example.com/presets/pending/" + owner.getId() + "/thumbnails/stub-thumb.png"))
				.andReturn();

		Long presetId = Long.valueOf(Pattern.compile("\"presetId\":(\\d+)")
				.matcher(createResult.getResponse().getContentAsString())
				.results()
				.findFirst()
				.orElseThrow()
				.group(1));
		Preset savedPreset = this.presetRepository.findById(presetId).orElseThrow();
		assertThat(savedPreset.getThumbnailRef())
				.isEqualTo("https://cdn.test.example.com/presets/pending/" + owner.getId() + "/thumbnails/stub-thumb.png");
	}

	@Test
	void finalizeThumbnailUploadUpdatesPresetRefForOwner() throws Exception {
		User owner = createUser("thumb-finalize");
		Preset preset = this.presetRepository.saveAndFlush(new Preset(
				owner.getId(),
				"Aurora Drift",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						""")));
		String accessToken = accessToken(login(owner.getEmail(), ownerPassword(owner.getEmail())));

		this.mockMvc.perform(post("/api/presets/" + preset.getId() + "/thumbnail/finalize")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"objectKey":"presets/%d/thumbnails/stub-thumb.png"}
						""".formatted(preset.getId())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.presetId").value(preset.getId()))
				.andExpect(jsonPath("$.thumbnailRef").value(
						"https://cdn.test.example.com/presets/" + preset.getId() + "/thumbnails/stub-thumb.png"));

		Preset refreshed = this.presetRepository.findById(preset.getId()).orElseThrow();
		assertThat(refreshed.getThumbnailRef())
				.isEqualTo("https://cdn.test.example.com/presets/" + preset.getId() + "/thumbnails/stub-thumb.png");
	}

	@Test
	void finalizeThumbnailUploadReturnsForbiddenWhenCallerIsNotOwner() throws Exception {
		User owner = createUser("thumb-finalize-owner");
		User other = createUser("thumb-finalize-other");
		Preset preset = this.presetRepository.saveAndFlush(new Preset(
				owner.getId(),
				"Owned Preset",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"pulse"}}
						""")));
		String otherAccessToken = accessToken(login(other.getEmail(), ownerPassword(other.getEmail())));

		this.mockMvc.perform(post("/api/presets/" + preset.getId() + "/thumbnail/finalize")
				.header("Authorization", "Bearer " + otherAccessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"objectKey":"presets/%d/thumbnails/stub-thumb.png"}
						""".formatted(preset.getId())))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("PRESET_OWNERSHIP_REQUIRED"))
				.andExpect(jsonPath("$.message").value("Preset ownership is required."));
	}

	@Test
	void finalizeThumbnailUploadReturnsNotFoundForNonexistentPreset() throws Exception {
		User user = createUser("thumb-finalize-missing");
		String accessToken = accessToken(login(user.getEmail(), ownerPassword(user.getEmail())));

		this.mockMvc.perform(post("/api/presets/99999/thumbnail/finalize")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"objectKey":"presets/99999/thumbnails/stub-thumb.png"}
						"""))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PRESET_NOT_FOUND"))
				.andExpect(jsonPath("$.message").value("Preset not found."));
	}

	@TestConfiguration
	static class StubThumbnailStorageConfiguration {

		@Bean
		@Primary
		ThumbnailStorageService stubThumbnailStorageService() {
			return new ThumbnailStorageService() {
				@Override
				public PresignedThumbnailUpload createPresetCreationUpload(Long ownerUserId, String filename, String contentType) {
					return new PresignedThumbnailUpload(
							"presets/pending/" + ownerUserId + "/thumbnails/stub-thumb.png",
							"https://upload.test.example.com/presets/pending/" + ownerUserId + "/thumbnails/stub-thumb.png",
							"PUT",
							java.util.Map.of("Content-Type", contentType),
							Instant.parse("2026-03-26T15:40:00Z"));
				}

				@Override
				public FinalizedThumbnail finalizePresetCreationUpload(Long ownerUserId, String objectKey) {
					return new FinalizedThumbnail(
							objectKey,
							"https://cdn.test.example.com/" + objectKey);
				}

				@Override
				public PresignedThumbnailUpload createPresignedUpload(Long presetId, String filename, String contentType) {
					return new PresignedThumbnailUpload(
							"presets/" + presetId + "/thumbnails/stub-thumb.png",
							"https://upload.test.example.com/presets/" + presetId + "/thumbnails/stub-thumb.png",
							"PUT",
							java.util.Map.of("Content-Type", contentType),
							Instant.parse("2026-03-26T15:40:00Z"));
				}

				@Override
				public FinalizedThumbnail finalizeUpload(Long presetId, String objectKey) {
					return new FinalizedThumbnail(
							objectKey,
							"https://cdn.test.example.com/" + objectKey);
				}
			};
		}
	}

	private User createUser(String emailPrefix) {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = emailPrefix + "-" + uniqueSuffix + "@example.com";
		String password = ownerPassword(email);

		return this.userRepository.saveAndFlush(new User(
				email,
				this.passwordHashingService.hash(password),
				emailPrefix + " user"));
	}

	private MvcResult login(String email, String password) throws Exception {
		return this.mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequestBody(email, password)))
				.andExpect(status().isOk())
				.andReturn();
	}

	private static String ownerPassword(String email) {
		return "password-" + email;
	}

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
