package com.bdmage.mage_backend.controller;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.bdmage.mage_backend.config.AuthenticatedUserRequest;
import com.bdmage.mage_backend.exception.ApiExceptionHandler;
import com.bdmage.mage_backend.exception.AuthenticationRequiredException;
import com.bdmage.mage_backend.exception.InvalidThumbnailException;
import com.bdmage.mage_backend.exception.SceneForbiddenException;
import com.bdmage.mage_backend.exception.SceneNotFoundException;
import com.bdmage.mage_backend.exception.SceneOwnershipRequiredException;
import com.bdmage.mage_backend.exception.SceneTagAlreadyExistsException;
import com.bdmage.mage_backend.exception.TagNotFoundException;
import com.bdmage.mage_backend.model.Scene;
import com.bdmage.mage_backend.model.SceneTag;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.repository.UserRepository;
import com.bdmage.mage_backend.service.SceneResponseFactory;
import com.bdmage.mage_backend.service.SceneService;
import com.bdmage.mage_backend.service.ThumbnailStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SceneControllerTests {

	private final ObjectMapper objectMapper = new ObjectMapper();

	private SceneService sceneService;
	private UserRepository userRepository;
	private MockMvc mockMvc;
	private LocalValidatorFactoryBean validator;

	@BeforeEach
	void setUp() {
		this.sceneService = mock(SceneService.class);
		this.userRepository = mock(UserRepository.class);
		this.validator = new LocalValidatorFactoryBean();
		this.validator.afterPropertiesSet();
		SceneResponseFactory sceneResponseFactory = new SceneResponseFactory(this.userRepository);
		this.mockMvc = MockMvcBuilders
				.standaloneSetup(new SceneController(this.sceneService, sceneResponseFactory))
				.setControllerAdvice(new ApiExceptionHandler())
				.setValidator(this.validator)
				.build();
	}

	@AfterEach
	void tearDown() {
		this.validator.close();
	}

	@Test
	void createSceneReturnsCreatedSceneForAuthenticatedUser() throws Exception {
		Scene scene = new Scene(
				77L,
				"Aurora Drift",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"},"state":{"energy":0.92}}
						"""));
		ReflectionTestUtils.setField(scene, "id", 15L);
		ReflectionTestUtils.setField(scene, "createdAt", Instant.parse("2026-03-26T15:30:00Z"));

		when(this.sceneService.createScene(
				77L,
				"Aurora Drift",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"},"state":{"energy":0.92}}
						"""),
				null))
				.thenReturn(scene);
		when(this.userRepository.findById(77L)).thenReturn(Optional.of(user(77L, "Scene Creator")));

		this.mockMvc.perform(post("/api/scenes")
				.requestAttr(AuthenticatedUserRequest.USER_ID_ATTRIBUTE, 77L)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "name":"Aurora Drift",
						  "sceneData":{"visualizer":{"shader":"nebula"},"state":{"energy":0.92}}
						}
						"""))
				.andExpect(status().isCreated())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.sceneId").value(15L))
				.andExpect(jsonPath("$.ownerUserId").value(77L))
				.andExpect(jsonPath("$.creatorDisplayName").value("Scene Creator"))
				.andExpect(jsonPath("$.name").value("Aurora Drift"))
				.andExpect(jsonPath("$.sceneData.visualizer.shader").value("nebula"))
				.andExpect(jsonPath("$.sceneData.state.energy").value(0.92))
				.andExpect(jsonPath("$.createdAt").value("2026-03-26T15:30:00Z"));
	}

	@Test
	void createSceneRejectsInvalidRequestBody() throws Exception {
		this.mockMvc.perform(post("/api/scenes")
				.requestAttr(AuthenticatedUserRequest.USER_ID_ATTRIBUTE, 77L)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "name":" "
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.details.name").value("name must not be blank"))
				.andExpect(jsonPath("$.details.sceneData").value("sceneData must not be null"));
	}

	@Test
	void createSceneAcceptsOptionalThumbnailObjectKey() throws Exception {
		Scene scene = new Scene(
				77L,
				"Aurora Drift",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						"""),
				"https://cdn.example.com/scenes/pending/77/thumbnails/abc123.png");
		ReflectionTestUtils.setField(scene, "id", 15L);
		ReflectionTestUtils.setField(scene, "createdAt", Instant.parse("2026-03-26T15:30:00Z"));

		when(this.sceneService.createScene(
				77L,
				"Aurora Drift",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						"""),
				"scenes/pending/77/thumbnails/abc123.png"))
				.thenReturn(scene);
		when(this.userRepository.findById(77L)).thenReturn(Optional.of(user(77L, "Scene Creator")));

		this.mockMvc.perform(post("/api/scenes")
				.requestAttr(AuthenticatedUserRequest.USER_ID_ATTRIBUTE, 77L)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "name":"Aurora Drift",
						  "sceneData":{"visualizer":{"shader":"nebula"}},
						  "thumbnailObjectKey":"scenes/pending/77/thumbnails/abc123.png"
						}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.sceneId").value(15L))
				.andExpect(jsonPath("$.creatorDisplayName").value("Scene Creator"))
				.andExpect(jsonPath("$.thumbnailRef").value("https://cdn.example.com/scenes/pending/77/thumbnails/abc123.png"));
	}

	@Test
	void createSceneReturnsUnauthorizedWhenRequestIsNotAuthenticated() throws Exception {
		when(this.sceneService.createScene(
				null,
				"Aurora Drift",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						"""),
				null))
				.thenThrow(new AuthenticationRequiredException("Authentication is required."));

		this.mockMvc.perform(post("/api/scenes")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "name":"Aurora Drift",
						  "sceneData":{"visualizer":{"shader":"nebula"}}
						}
						"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
				.andExpect(jsonPath("$.message").value("Authentication is required."));
	}

	@Test
	void getScenesReturnsAllScenesWhenTagFilterIsMissing() throws Exception {
		Scene firstScene = new Scene(
				77L,
				"Aurora Drift",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						"""));
		Scene secondScene = new Scene(
				78L,
				"Signal Bloom",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"pulse"}}
						"""));
		ReflectionTestUtils.setField(firstScene, "id", 15L);
		ReflectionTestUtils.setField(secondScene, "id", 16L);
		ReflectionTestUtils.setField(firstScene, "createdAt", Instant.parse("2026-03-26T15:30:00Z"));
		ReflectionTestUtils.setField(secondScene, "createdAt", Instant.parse("2026-03-26T16:30:00Z"));

		when(this.sceneService.getAllScenes()).thenReturn(List.of(firstScene, secondScene));
		when(this.userRepository.findAllById(java.util.Set.of(77L, 78L)))
				.thenReturn(List.of(user(77L, "Aurora Artist"), user(78L, "Signal Artist")));

		this.mockMvc.perform(get("/api/scenes"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$[0].sceneId").value(15L))
				.andExpect(jsonPath("$[0].creatorDisplayName").value("Aurora Artist"))
				.andExpect(jsonPath("$[0].name").value("Aurora Drift"))
				.andExpect(jsonPath("$[1].sceneId").value(16L))
				.andExpect(jsonPath("$[1].creatorDisplayName").value("Signal Artist"))
				.andExpect(jsonPath("$[1].name").value("Signal Bloom"));
	}

	@Test
	void getScenesReturnsFilteredScenesWhenTagFilterIsProvided() throws Exception {
		Scene scene = new Scene(
				77L,
				"Aurora Drift",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						"""));
		ReflectionTestUtils.setField(scene, "id", 15L);
		ReflectionTestUtils.setField(scene, "createdAt", Instant.parse("2026-03-26T15:30:00Z"));

		when(this.sceneService.getScenesByTag("ambient")).thenReturn(List.of(scene));
		when(this.userRepository.findAllById(java.util.Set.of(77L)))
				.thenReturn(List.of(user(77L, "Aurora Artist")));

		this.mockMvc.perform(get("/api/scenes").param("tag", "ambient"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$[0].sceneId").value(15L))
				.andExpect(jsonPath("$[0].creatorDisplayName").value("Aurora Artist"))
				.andExpect(jsonPath("$[0].name").value("Aurora Drift"));
	}

	@Test
	void attachTagToSceneReturnsCreatedAssociationForAuthenticatedUser() throws Exception {
		SceneTag sceneTag = new SceneTag(15L, 7L);

		when(this.sceneService.attachTagToScene(77L, 15L, 7L))
				.thenReturn(sceneTag);

		this.mockMvc.perform(post("/api/scenes/15/tags")
				.requestAttr(AuthenticatedUserRequest.USER_ID_ATTRIBUTE, 77L)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"tagId":7}
						"""))
				.andExpect(status().isCreated())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.sceneId").value(15L))
				.andExpect(jsonPath("$.tagId").value(7L));
	}

	@Test
	void attachTagToSceneReturnsUnauthorizedWhenRequestIsNotAuthenticated() throws Exception {
		when(this.sceneService.attachTagToScene(null, 15L, 7L))
				.thenThrow(new AuthenticationRequiredException("Authentication is required."));

		this.mockMvc.perform(post("/api/scenes/15/tags")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"tagId":7}
						"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
				.andExpect(jsonPath("$.message").value("Authentication is required."));
	}

	@Test
	void attachTagToSceneReturnsNotFoundWhenTagDoesNotExist() throws Exception {
		when(this.sceneService.attachTagToScene(77L, 15L, 7L))
				.thenThrow(new TagNotFoundException("Tag not found."));

		this.mockMvc.perform(post("/api/scenes/15/tags")
				.requestAttr(AuthenticatedUserRequest.USER_ID_ATTRIBUTE, 77L)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"tagId":7}
						"""))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("TAG_NOT_FOUND"))
				.andExpect(jsonPath("$.message").value("Tag not found."));
	}

	@Test
	void attachTagToSceneReturnsConflictWhenAssociationAlreadyExists() throws Exception {
		when(this.sceneService.attachTagToScene(77L, 15L, 7L))
				.thenThrow(new SceneTagAlreadyExistsException("This tag is already attached to the scene."));

		this.mockMvc.perform(post("/api/scenes/15/tags")
				.requestAttr(AuthenticatedUserRequest.USER_ID_ATTRIBUTE, 77L)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"tagId":7}
						"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SCENE_TAG_ALREADY_EXISTS"))
				.andExpect(jsonPath("$.message").value("This tag is already attached to the scene."));
	}

	@Test
	void getSceneReturnsSceneById() throws Exception {
		Scene scene = new Scene(
				77L,
				"Aurora Drift",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"},"state":{"energy":0.92}}
						"""),
				"https://cdn.example.com/scenes/15/thumbnails/thumb.png");
		ReflectionTestUtils.setField(scene, "id", 15L);
		ReflectionTestUtils.setField(scene, "createdAt", Instant.parse("2026-03-26T15:30:00Z"));

		when(this.sceneService.getScene(15L)).thenReturn(scene);
		when(this.sceneService.getTagNamesForScene(15L)).thenReturn(List.of("ambient", "showcase"));
		when(this.userRepository.findById(77L)).thenReturn(Optional.of(user(77L, "Scene Creator")));

		this.mockMvc.perform(get("/api/scenes/15"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.sceneId").value(15L))
				.andExpect(jsonPath("$.ownerUserId").value(77L))
				.andExpect(jsonPath("$.creatorDisplayName").value("Scene Creator"))
				.andExpect(jsonPath("$.name").value("Aurora Drift"))
				.andExpect(jsonPath("$.sceneData.visualizer.shader").value("nebula"))
				.andExpect(jsonPath("$.sceneData.state.energy").value(0.92))
				.andExpect(jsonPath("$.thumbnailRef").value("https://cdn.example.com/scenes/15/thumbnails/thumb.png"))
				.andExpect(jsonPath("$.createdAt").value("2026-03-26T15:30:00Z"))
				.andExpect(jsonPath("$.tags[0]").value("ambient"))
				.andExpect(jsonPath("$.tags[1]").value("showcase"));
	}

	@Test
	void getSceneReturnsNotFoundWhenSceneDoesNotExist() throws Exception {
		when(this.sceneService.getScene(99999L))
				.thenThrow(new SceneNotFoundException("Scene not found."));

		this.mockMvc.perform(get("/api/scenes/99999"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("SCENE_NOT_FOUND"))
				.andExpect(jsonPath("$.message").value("Scene not found."));
	}

	@Test
	void deleteSceneReturnsNoContentForAuthenticatedOwner() throws Exception {
		this.mockMvc.perform(delete("/api/scenes/15")
				.requestAttr(AuthenticatedUserRequest.USER_ID_ATTRIBUTE, 77L))
				.andExpect(status().isNoContent())
				.andExpect(content().string(""));
	}

	@Test
	void deleteSceneReturnsUnauthorizedWhenRequestIsNotAuthenticated() throws Exception {
		doThrow(new AuthenticationRequiredException("Authentication is required."))
				.when(this.sceneService)
				.deleteScene(null, 15L);

		this.mockMvc.perform(delete("/api/scenes/15"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
				.andExpect(jsonPath("$.message").value("Authentication is required."));
	}

	@Test
	void deleteSceneReturnsForbiddenWhenAuthenticatedUserDoesNotOwnScene() throws Exception {
		doThrow(new SceneForbiddenException("You do not have permission to delete this scene."))
				.when(this.sceneService)
				.deleteScene(77L, 15L);

		this.mockMvc.perform(delete("/api/scenes/15")
				.requestAttr(AuthenticatedUserRequest.USER_ID_ATTRIBUTE, 77L))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("SCENE_FORBIDDEN"))
				.andExpect(jsonPath("$.message").value("You do not have permission to delete this scene."));
	}

	@Test
	void createSceneThumbnailUploadReturnsPresignedPayload() throws Exception {
		ThumbnailStorageService.PresignedThumbnailUpload upload = new ThumbnailStorageService.PresignedThumbnailUpload(
				"scenes/pending/77/thumbnails/abc123.png",
				"https://upload.example.com/scenes/pending/77/thumbnails/abc123.png",
				"PUT",
				Map.of("Content-Type", "image/png"),
				Instant.parse("2026-03-26T15:40:00Z"));

		when(this.sceneService.createSceneThumbnailUpload(77L, "thumb.png", "image/png", 3L))
				.thenReturn(upload);

		this.mockMvc.perform(post("/api/scenes/thumbnail/presign")
				.requestAttr(AuthenticatedUserRequest.USER_ID_ATTRIBUTE, 77L)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "filename":"thumb.png",
						  "contentType":"image/png",
						  "sizeBytes":3
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.objectKey").value("scenes/pending/77/thumbnails/abc123.png"))
				.andExpect(jsonPath("$.uploadUrl").value("https://upload.example.com/scenes/pending/77/thumbnails/abc123.png"))
				.andExpect(jsonPath("$.method").value("PUT"))
				.andExpect(jsonPath("$.headers.Content-Type").value("image/png"))
				.andExpect(jsonPath("$.expiresAt").value("2026-03-26T15:40:00Z"));
	}

	@Test
	void createSceneThumbnailUploadReturnsBadRequestForInvalidPayload() throws Exception {
		this.mockMvc.perform(post("/api/scenes/thumbnail/presign")
				.requestAttr(AuthenticatedUserRequest.USER_ID_ATTRIBUTE, 77L)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "filename":" ",
						  "contentType":" ",
						  "sizeBytes":0
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.details.filename").value("filename must not be blank"))
				.andExpect(jsonPath("$.details.contentType").value("contentType must not be blank"))
				.andExpect(jsonPath("$.details.sizeBytes").value("sizeBytes must be greater than zero"));
	}

	@Test
	void createSceneThumbnailUploadReturnsBadRequestForInvalidFileMetadata() throws Exception {
		when(this.sceneService.createSceneThumbnailUpload(77L, "doc.pdf", "application/pdf", 3L))
				.thenThrow(new InvalidThumbnailException("Thumbnail must be a valid image (jpeg, png, webp, or gif)."));

		this.mockMvc.perform(post("/api/scenes/thumbnail/presign")
				.requestAttr(AuthenticatedUserRequest.USER_ID_ATTRIBUTE, 77L)
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
	void createThumbnailUploadReturnsForbiddenWhenCallerIsNotOwner() throws Exception {
		when(this.sceneService.createThumbnailUpload(88L, 15L, "thumb.png", "image/png", 3L))
				.thenThrow(new SceneOwnershipRequiredException("Scene ownership is required."));

		this.mockMvc.perform(post("/api/scenes/15/thumbnail/presign")
				.requestAttr(AuthenticatedUserRequest.USER_ID_ATTRIBUTE, 88L)
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
	void finalizeThumbnailUploadReturnsOkWithUpdatedScene() throws Exception {
		Scene updatedScene = new Scene(
				77L,
				"Aurora Drift",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						"""),
				"https://cdn.example.com/scenes/15/thumbnails/new-thumb.png");
		ReflectionTestUtils.setField(updatedScene, "id", 15L);
		ReflectionTestUtils.setField(updatedScene, "createdAt", Instant.parse("2026-03-26T15:30:00Z"));

		when(this.sceneService.finalizeThumbnailUpload(77L, 15L, "scenes/15/thumbnails/new-thumb.png"))
				.thenReturn(updatedScene);
		when(this.userRepository.findById(77L)).thenReturn(Optional.of(user(77L, "Scene Creator")));

		this.mockMvc.perform(post("/api/scenes/15/thumbnail/finalize")
				.requestAttr(AuthenticatedUserRequest.USER_ID_ATTRIBUTE, 77L)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"objectKey":"scenes/15/thumbnails/new-thumb.png"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sceneId").value(15L))
				.andExpect(jsonPath("$.creatorDisplayName").value("Scene Creator"))
				.andExpect(jsonPath("$.thumbnailRef").value("https://cdn.example.com/scenes/15/thumbnails/new-thumb.png"));
	}

	@Test
	void finalizeThumbnailUploadReturnsNotFoundWhenSceneDoesNotExist() throws Exception {
		when(this.sceneService.finalizeThumbnailUpload(77L, 99999L, "scenes/99999/thumbnails/new-thumb.png"))
				.thenThrow(new SceneNotFoundException("Scene not found."));

		this.mockMvc.perform(post("/api/scenes/99999/thumbnail/finalize")
				.requestAttr(AuthenticatedUserRequest.USER_ID_ATTRIBUTE, 77L)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"objectKey":"scenes/99999/thumbnails/new-thumb.png"}
						"""))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("SCENE_NOT_FOUND"))
				.andExpect(jsonPath("$.message").value("Scene not found."));
	}

	@Test
	void finalizeThumbnailUploadReturnsForbiddenWhenCallerIsNotOwner() throws Exception {
		when(this.sceneService.finalizeThumbnailUpload(88L, 15L, "scenes/15/thumbnails/new-thumb.png"))
				.thenThrow(new SceneOwnershipRequiredException("Scene ownership is required."));

		this.mockMvc.perform(post("/api/scenes/15/thumbnail/finalize")
				.requestAttr(AuthenticatedUserRequest.USER_ID_ATTRIBUTE, 88L)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"objectKey":"scenes/15/thumbnails/new-thumb.png"}
						"""))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("SCENE_OWNERSHIP_REQUIRED"))
				.andExpect(jsonPath("$.message").value("Scene ownership is required."));
	}

	private User user(Long userId, String displayName) {
		User user = new User("user-" + userId + "@example.com", "hashed-password", displayName);
		ReflectionTestUtils.setField(user, "id", userId);
		return user;
	}
}
