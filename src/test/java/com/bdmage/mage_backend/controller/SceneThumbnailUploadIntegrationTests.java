package com.bdmage.mage_backend.controller;

import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.bdmage.mage_backend.model.Scene;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.repository.SceneRepository;
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
@Import(SceneThumbnailUploadIntegrationTests.StubThumbnailStorageConfiguration.class)
class SceneThumbnailUploadIntegrationTests extends PostgresIntegrationTestSupport {

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
	void createSceneThumbnailUploadReturnsUnauthorizedWhenRequestHasNoAuthenticationHeader() throws Exception {
		this.mockMvc.perform(post("/api/scenes/thumbnail/presign")
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
		this.mockMvc.perform(post("/api/scenes/15/thumbnail/presign")
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
	void createSceneThumbnailUploadReturnsPresignedPayloadForAuthenticatedUser() throws Exception {
		User owner = createUser("new-thumb-owner");
		String accessToken = accessToken(login(owner.getEmail(), ownerPassword(owner.getEmail())));

		this.mockMvc.perform(post("/api/scenes/thumbnail/presign")
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
				.andExpect(jsonPath("$.objectKey").value("scenes/pending/" + owner.getId() + "/thumbnails/stub-thumb.png"))
				.andExpect(jsonPath("$.method").value("PUT"))
				.andExpect(jsonPath("$.headers.Content-Type").value("image/png"));
	}

	@Test
	void createThumbnailUploadReturnsPresignedPayloadForOwner() throws Exception {
		User owner = createUser("thumb-owner");
		Scene scene = this.sceneRepository.saveAndFlush(new Scene(
				owner.getId(),
				"Aurora Drift",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						""")));

		String accessToken = accessToken(login(owner.getEmail(), ownerPassword(owner.getEmail())));

		this.mockMvc.perform(post("/api/scenes/" + scene.getId() + "/thumbnail/presign")
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
				.andExpect(jsonPath("$.objectKey").value("scenes/" + scene.getId() + "/thumbnails/stub-thumb.png"))
				.andExpect(jsonPath("$.method").value("PUT"))
				.andExpect(jsonPath("$.headers.Content-Type").value("image/png"));
	}

	@Test
	void createThumbnailUploadReturnsForbiddenWhenCallerIsNotOwner() throws Exception {
		User owner = createUser("thumb-owner2");
		User other = createUser("thumb-other");
		Scene scene = this.sceneRepository.saveAndFlush(new Scene(
				owner.getId(),
				"Owned Scene",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"pulse"}}
						""")));

		String otherAccessToken = accessToken(login(other.getEmail(), ownerPassword(other.getEmail())));

		this.mockMvc.perform(post("/api/scenes/" + scene.getId() + "/thumbnail/presign")
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
				.andExpect(jsonPath("$.code").value("SCENE_OWNERSHIP_REQUIRED"))
				.andExpect(jsonPath("$.message").value("Scene ownership is required."));
	}

	@Test
	void createThumbnailUploadReturnsNotFoundForNonexistentScene() throws Exception {
		User user = createUser("thumb-missing");
		String accessToken = accessToken(login(user.getEmail(), ownerPassword(user.getEmail())));

		this.mockMvc.perform(post("/api/scenes/99999/thumbnail/presign")
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
				.andExpect(jsonPath("$.code").value("SCENE_NOT_FOUND"))
				.andExpect(jsonPath("$.message").value("Scene not found."));
	}

	@Test
	void createThumbnailUploadReturnsBadRequestForInvalidContentType() throws Exception {
		User owner = createUser("thumb-invalid");
		Scene scene = this.sceneRepository.saveAndFlush(new Scene(
				owner.getId(),
				"Aurora Drift",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						""")));
		String accessToken = accessToken(login(owner.getEmail(), ownerPassword(owner.getEmail())));

		this.mockMvc.perform(post("/api/scenes/" + scene.getId() + "/thumbnail/presign")
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
	void createSceneWithThumbnailObjectKeyPersistsThumbnailRefOnlyAfterFinalize() throws Exception {
		User owner = createUser("thumb-create");
		String accessToken = accessToken(login(owner.getEmail(), ownerPassword(owner.getEmail())));

		MvcResult createResult = this.mockMvc.perform(post("/api/scenes")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "name":"Aurora Drift",
						  "sceneData":{"visualizer":{"shader":"nebula"}},
						  "thumbnailObjectKey":"scenes/pending/%d/thumbnails/stub-thumb.png"
						}
						""".formatted(owner.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.thumbnailRef").value(
						"https://cdn.test.example.com/scenes/pending/" + owner.getId() + "/thumbnails/stub-thumb.png"))
				.andReturn();

		Long sceneId = Long.valueOf(Pattern.compile("\"sceneId\":(\\d+)")
				.matcher(createResult.getResponse().getContentAsString())
				.results()
				.findFirst()
				.orElseThrow()
				.group(1));
		Scene savedScene = this.sceneRepository.findById(sceneId).orElseThrow();
		assertThat(savedScene.getThumbnailRef())
				.isEqualTo("https://cdn.test.example.com/scenes/pending/" + owner.getId() + "/thumbnails/stub-thumb.png");
	}

	@Test
	void finalizeThumbnailUploadUpdatesSceneRefForOwner() throws Exception {
		User owner = createUser("thumb-finalize");
		Scene scene = this.sceneRepository.saveAndFlush(new Scene(
				owner.getId(),
				"Aurora Drift",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						""")));
		String accessToken = accessToken(login(owner.getEmail(), ownerPassword(owner.getEmail())));

		this.mockMvc.perform(post("/api/scenes/" + scene.getId() + "/thumbnail/finalize")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"objectKey":"scenes/%d/thumbnails/stub-thumb.png"}
						""".formatted(scene.getId())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sceneId").value(scene.getId()))
				.andExpect(jsonPath("$.thumbnailRef").value(
						"https://cdn.test.example.com/scenes/" + scene.getId() + "/thumbnails/stub-thumb.png"));

		Scene refreshed = this.sceneRepository.findById(scene.getId()).orElseThrow();
		assertThat(refreshed.getThumbnailRef())
				.isEqualTo("https://cdn.test.example.com/scenes/" + scene.getId() + "/thumbnails/stub-thumb.png");
	}

	@Test
	void finalizeThumbnailUploadReturnsForbiddenWhenCallerIsNotOwner() throws Exception {
		User owner = createUser("thumb-finalize-owner");
		User other = createUser("thumb-finalize-other");
		Scene scene = this.sceneRepository.saveAndFlush(new Scene(
				owner.getId(),
				"Owned Scene",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"pulse"}}
						""")));
		String otherAccessToken = accessToken(login(other.getEmail(), ownerPassword(other.getEmail())));

		this.mockMvc.perform(post("/api/scenes/" + scene.getId() + "/thumbnail/finalize")
				.header("Authorization", "Bearer " + otherAccessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"objectKey":"scenes/%d/thumbnails/stub-thumb.png"}
						""".formatted(scene.getId())))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("SCENE_OWNERSHIP_REQUIRED"))
				.andExpect(jsonPath("$.message").value("Scene ownership is required."));
	}

	@Test
	void finalizeThumbnailUploadReturnsNotFoundForNonexistentScene() throws Exception {
		User user = createUser("thumb-finalize-missing");
		String accessToken = accessToken(login(user.getEmail(), ownerPassword(user.getEmail())));

		this.mockMvc.perform(post("/api/scenes/99999/thumbnail/finalize")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"objectKey":"scenes/99999/thumbnails/stub-thumb.png"}
						"""))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("SCENE_NOT_FOUND"))
				.andExpect(jsonPath("$.message").value("Scene not found."));
	}

	@TestConfiguration
	static class StubThumbnailStorageConfiguration {

		@Bean
		@Primary
		ThumbnailStorageService stubThumbnailStorageService() {
			return new ThumbnailStorageService() {
				@Override
				public PresignedThumbnailUpload createSceneCreationUpload(Long ownerUserId, String filename, String contentType) {
					return new PresignedThumbnailUpload(
							"scenes/pending/" + ownerUserId + "/thumbnails/stub-thumb.png",
							"https://upload.test.example.com/scenes/pending/" + ownerUserId + "/thumbnails/stub-thumb.png",
							"PUT",
							java.util.Map.of("Content-Type", contentType),
							Instant.parse("2026-03-26T15:40:00Z"));
				}

				@Override
				public FinalizedThumbnail finalizeSceneCreationUpload(Long ownerUserId, String objectKey) {
					return new FinalizedThumbnail(
							objectKey,
							"https://cdn.test.example.com/" + objectKey);
				}

				@Override
				public PresignedThumbnailUpload createPresignedUpload(Long sceneId, String filename, String contentType) {
					return new PresignedThumbnailUpload(
							"scenes/" + sceneId + "/thumbnails/stub-thumb.png",
							"https://upload.test.example.com/scenes/" + sceneId + "/thumbnails/stub-thumb.png",
							"PUT",
							java.util.Map.of("Content-Type", contentType),
							Instant.parse("2026-03-26T15:40:00Z"));
				}

				@Override
				public FinalizedThumbnail finalizeUpload(Long sceneId, String objectKey) {
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
