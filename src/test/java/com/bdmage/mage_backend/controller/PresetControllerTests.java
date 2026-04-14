package com.bdmage.mage_backend.controller;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.bdmage.mage_backend.config.AuthenticatedUserRequest;
import com.bdmage.mage_backend.exception.ApiExceptionHandler;
import com.bdmage.mage_backend.exception.AuthenticationRequiredException;
import com.bdmage.mage_backend.exception.InvalidThumbnailException;
import com.bdmage.mage_backend.exception.PresetForbiddenException;
import com.bdmage.mage_backend.exception.PresetNotFoundException;
import com.bdmage.mage_backend.exception.PresetOwnershipRequiredException;
import com.bdmage.mage_backend.exception.PresetTagAlreadyExistsException;
import com.bdmage.mage_backend.exception.TagNotFoundException;
import com.bdmage.mage_backend.model.Preset;
import com.bdmage.mage_backend.model.PresetTag;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.repository.UserRepository;
import com.bdmage.mage_backend.service.PresetResponseFactory;
import com.bdmage.mage_backend.service.PresetService;
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

class PresetControllerTests {

	private final ObjectMapper objectMapper = new ObjectMapper();

	private PresetService presetService;
	private UserRepository userRepository;
	private MockMvc mockMvc;
	private LocalValidatorFactoryBean validator;

	@BeforeEach
	void setUp() {
		this.presetService = mock(PresetService.class);
		this.userRepository = mock(UserRepository.class);
		this.validator = new LocalValidatorFactoryBean();
		this.validator.afterPropertiesSet();
		PresetResponseFactory presetResponseFactory = new PresetResponseFactory(this.userRepository);
		this.mockMvc = MockMvcBuilders
				.standaloneSetup(new PresetController(this.presetService, presetResponseFactory))
				.setControllerAdvice(new ApiExceptionHandler())
				.setValidator(this.validator)
				.build();
	}

	@AfterEach
	void tearDown() {
		this.validator.close();
	}

	@Test
	void createPresetReturnsCreatedPresetForAuthenticatedUser() throws Exception {
		Preset preset = new Preset(
				77L,
				"Aurora Drift",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"},"state":{"energy":0.92}}
						"""));
		ReflectionTestUtils.setField(preset, "id", 15L);
		ReflectionTestUtils.setField(preset, "createdAt", Instant.parse("2026-03-26T15:30:00Z"));

		when(this.presetService.createPreset(
				77L,
				"Aurora Drift",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"},"state":{"energy":0.92}}
						"""),
				null))
				.thenReturn(preset);
		when(this.userRepository.findById(77L)).thenReturn(Optional.of(user(77L, "Preset Creator")));

		this.mockMvc.perform(post("/api/presets")
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
				.andExpect(jsonPath("$.presetId").value(15L))
				.andExpect(jsonPath("$.ownerUserId").value(77L))
				.andExpect(jsonPath("$.creatorDisplayName").value("Preset Creator"))
				.andExpect(jsonPath("$.name").value("Aurora Drift"))
				.andExpect(jsonPath("$.sceneData.visualizer.shader").value("nebula"))
				.andExpect(jsonPath("$.sceneData.state.energy").value(0.92))
				.andExpect(jsonPath("$.createdAt").value("2026-03-26T15:30:00Z"));
	}

	@Test
	void createPresetRejectsInvalidRequestBody() throws Exception {
		this.mockMvc.perform(post("/api/presets")
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
	void createPresetAcceptsOptionalThumbnailObjectKey() throws Exception {
		Preset preset = new Preset(
				77L,
				"Aurora Drift",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						"""),
				"https://cdn.example.com/presets/pending/77/thumbnails/abc123.png");
		ReflectionTestUtils.setField(preset, "id", 15L);
		ReflectionTestUtils.setField(preset, "createdAt", Instant.parse("2026-03-26T15:30:00Z"));

		when(this.presetService.createPreset(
				77L,
				"Aurora Drift",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						"""),
				"presets/pending/77/thumbnails/abc123.png"))
				.thenReturn(preset);
		when(this.userRepository.findById(77L)).thenReturn(Optional.of(user(77L, "Preset Creator")));

		this.mockMvc.perform(post("/api/presets")
				.requestAttr(AuthenticatedUserRequest.USER_ID_ATTRIBUTE, 77L)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "name":"Aurora Drift",
						  "sceneData":{"visualizer":{"shader":"nebula"}},
						  "thumbnailObjectKey":"presets/pending/77/thumbnails/abc123.png"
						}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.presetId").value(15L))
				.andExpect(jsonPath("$.creatorDisplayName").value("Preset Creator"))
				.andExpect(jsonPath("$.thumbnailRef").value("https://cdn.example.com/presets/pending/77/thumbnails/abc123.png"));
	}

	@Test
	void createPresetReturnsUnauthorizedWhenRequestIsNotAuthenticated() throws Exception {
		when(this.presetService.createPreset(
				null,
				"Aurora Drift",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						"""),
				null))
				.thenThrow(new AuthenticationRequiredException("Authentication is required."));

		this.mockMvc.perform(post("/api/presets")
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
	void getPresetsReturnsAllPresetsWhenTagFilterIsMissing() throws Exception {
		Preset firstPreset = new Preset(
				77L,
				"Aurora Drift",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						"""));
		Preset secondPreset = new Preset(
				78L,
				"Signal Bloom",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"pulse"}}
						"""));
		ReflectionTestUtils.setField(firstPreset, "id", 15L);
		ReflectionTestUtils.setField(secondPreset, "id", 16L);
		ReflectionTestUtils.setField(firstPreset, "createdAt", Instant.parse("2026-03-26T15:30:00Z"));
		ReflectionTestUtils.setField(secondPreset, "createdAt", Instant.parse("2026-03-26T16:30:00Z"));

		when(this.presetService.getAllPresets()).thenReturn(List.of(firstPreset, secondPreset));
		when(this.userRepository.findAllById(java.util.Set.of(77L, 78L)))
				.thenReturn(List.of(user(77L, "Aurora Artist"), user(78L, "Signal Artist")));

		this.mockMvc.perform(get("/api/presets"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$[0].presetId").value(15L))
				.andExpect(jsonPath("$[0].creatorDisplayName").value("Aurora Artist"))
				.andExpect(jsonPath("$[0].name").value("Aurora Drift"))
				.andExpect(jsonPath("$[1].presetId").value(16L))
				.andExpect(jsonPath("$[1].creatorDisplayName").value("Signal Artist"))
				.andExpect(jsonPath("$[1].name").value("Signal Bloom"));
	}

	@Test
	void getPresetsReturnsFilteredPresetsWhenTagFilterIsProvided() throws Exception {
		Preset preset = new Preset(
				77L,
				"Aurora Drift",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						"""));
		ReflectionTestUtils.setField(preset, "id", 15L);
		ReflectionTestUtils.setField(preset, "createdAt", Instant.parse("2026-03-26T15:30:00Z"));

		when(this.presetService.getPresetsByTag("ambient")).thenReturn(List.of(preset));
		when(this.userRepository.findAllById(java.util.Set.of(77L)))
				.thenReturn(List.of(user(77L, "Aurora Artist")));

		this.mockMvc.perform(get("/api/presets").param("tag", "ambient"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$[0].presetId").value(15L))
				.andExpect(jsonPath("$[0].creatorDisplayName").value("Aurora Artist"))
				.andExpect(jsonPath("$[0].name").value("Aurora Drift"));
	}

	@Test
	void attachTagToPresetReturnsCreatedAssociationForAuthenticatedUser() throws Exception {
		PresetTag presetTag = new PresetTag(15L, 7L);

		when(this.presetService.attachTagToPreset(77L, 15L, 7L))
				.thenReturn(presetTag);

		this.mockMvc.perform(post("/api/presets/15/tags")
				.requestAttr(AuthenticatedUserRequest.USER_ID_ATTRIBUTE, 77L)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"tagId":7}
						"""))
				.andExpect(status().isCreated())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.presetId").value(15L))
				.andExpect(jsonPath("$.tagId").value(7L));
	}

	@Test
	void attachTagToPresetReturnsUnauthorizedWhenRequestIsNotAuthenticated() throws Exception {
		when(this.presetService.attachTagToPreset(null, 15L, 7L))
				.thenThrow(new AuthenticationRequiredException("Authentication is required."));

		this.mockMvc.perform(post("/api/presets/15/tags")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"tagId":7}
						"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
				.andExpect(jsonPath("$.message").value("Authentication is required."));
	}

	@Test
	void attachTagToPresetReturnsNotFoundWhenTagDoesNotExist() throws Exception {
		when(this.presetService.attachTagToPreset(77L, 15L, 7L))
				.thenThrow(new TagNotFoundException("Tag not found."));

		this.mockMvc.perform(post("/api/presets/15/tags")
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
	void attachTagToPresetReturnsConflictWhenAssociationAlreadyExists() throws Exception {
		when(this.presetService.attachTagToPreset(77L, 15L, 7L))
				.thenThrow(new PresetTagAlreadyExistsException("This tag is already attached to the preset."));

		this.mockMvc.perform(post("/api/presets/15/tags")
				.requestAttr(AuthenticatedUserRequest.USER_ID_ATTRIBUTE, 77L)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"tagId":7}
						"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PRESET_TAG_ALREADY_EXISTS"))
				.andExpect(jsonPath("$.message").value("This tag is already attached to the preset."));
	}

	@Test
	void getPresetReturnsPresetById() throws Exception {
		Preset preset = new Preset(
				77L,
				"Aurora Drift",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"},"state":{"energy":0.92}}
						"""),
				"https://cdn.example.com/presets/15/thumbnails/thumb.png");
		ReflectionTestUtils.setField(preset, "id", 15L);
		ReflectionTestUtils.setField(preset, "createdAt", Instant.parse("2026-03-26T15:30:00Z"));

		when(this.presetService.getPreset(15L)).thenReturn(preset);
		when(this.userRepository.findById(77L)).thenReturn(Optional.of(user(77L, "Preset Creator")));

		this.mockMvc.perform(get("/api/presets/15"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.presetId").value(15L))
				.andExpect(jsonPath("$.ownerUserId").value(77L))
				.andExpect(jsonPath("$.creatorDisplayName").value("Preset Creator"))
				.andExpect(jsonPath("$.name").value("Aurora Drift"))
				.andExpect(jsonPath("$.sceneData.visualizer.shader").value("nebula"))
				.andExpect(jsonPath("$.sceneData.state.energy").value(0.92))
				.andExpect(jsonPath("$.thumbnailRef").value("https://cdn.example.com/presets/15/thumbnails/thumb.png"))
				.andExpect(jsonPath("$.createdAt").value("2026-03-26T15:30:00Z"));
	}

	@Test
	void getPresetReturnsNotFoundWhenPresetDoesNotExist() throws Exception {
		when(this.presetService.getPreset(99999L))
				.thenThrow(new PresetNotFoundException("Preset not found."));

		this.mockMvc.perform(get("/api/presets/99999"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PRESET_NOT_FOUND"))
				.andExpect(jsonPath("$.message").value("Preset not found."));
	}

	@Test
	void deletePresetReturnsNoContentForAuthenticatedOwner() throws Exception {
		this.mockMvc.perform(delete("/api/presets/15")
				.requestAttr(AuthenticatedUserRequest.USER_ID_ATTRIBUTE, 77L))
				.andExpect(status().isNoContent())
				.andExpect(content().string(""));
	}

	@Test
	void deletePresetReturnsUnauthorizedWhenRequestIsNotAuthenticated() throws Exception {
		doThrow(new AuthenticationRequiredException("Authentication is required."))
				.when(this.presetService)
				.deletePreset(null, 15L);

		this.mockMvc.perform(delete("/api/presets/15"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
				.andExpect(jsonPath("$.message").value("Authentication is required."));
	}

	@Test
	void deletePresetReturnsForbiddenWhenAuthenticatedUserDoesNotOwnPreset() throws Exception {
		doThrow(new PresetForbiddenException("You do not have permission to delete this preset."))
				.when(this.presetService)
				.deletePreset(77L, 15L);

		this.mockMvc.perform(delete("/api/presets/15")
				.requestAttr(AuthenticatedUserRequest.USER_ID_ATTRIBUTE, 77L))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("PRESET_FORBIDDEN"))
				.andExpect(jsonPath("$.message").value("You do not have permission to delete this preset."));
	}

	@Test
	void createPresetThumbnailUploadReturnsPresignedPayload() throws Exception {
		ThumbnailStorageService.PresignedThumbnailUpload upload = new ThumbnailStorageService.PresignedThumbnailUpload(
				"presets/pending/77/thumbnails/abc123.png",
				"https://upload.example.com/presets/pending/77/thumbnails/abc123.png",
				"PUT",
				Map.of("Content-Type", "image/png"),
				Instant.parse("2026-03-26T15:40:00Z"));

		when(this.presetService.createPresetThumbnailUpload(77L, "thumb.png", "image/png", 3L))
				.thenReturn(upload);

		this.mockMvc.perform(post("/api/presets/thumbnail/presign")
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
				.andExpect(jsonPath("$.objectKey").value("presets/pending/77/thumbnails/abc123.png"))
				.andExpect(jsonPath("$.uploadUrl").value("https://upload.example.com/presets/pending/77/thumbnails/abc123.png"))
				.andExpect(jsonPath("$.method").value("PUT"))
				.andExpect(jsonPath("$.headers.Content-Type").value("image/png"))
				.andExpect(jsonPath("$.expiresAt").value("2026-03-26T15:40:00Z"));
	}

	@Test
	void createPresetThumbnailUploadReturnsBadRequestForInvalidPayload() throws Exception {
		this.mockMvc.perform(post("/api/presets/thumbnail/presign")
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
	void createPresetThumbnailUploadReturnsBadRequestForInvalidFileMetadata() throws Exception {
		when(this.presetService.createPresetThumbnailUpload(77L, "doc.pdf", "application/pdf", 3L))
				.thenThrow(new InvalidThumbnailException("Thumbnail must be a valid image (jpeg, png, webp, or gif)."));

		this.mockMvc.perform(post("/api/presets/thumbnail/presign")
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
		when(this.presetService.createThumbnailUpload(88L, 15L, "thumb.png", "image/png", 3L))
				.thenThrow(new PresetOwnershipRequiredException("Preset ownership is required."));

		this.mockMvc.perform(post("/api/presets/15/thumbnail/presign")
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
				.andExpect(jsonPath("$.code").value("PRESET_OWNERSHIP_REQUIRED"))
				.andExpect(jsonPath("$.message").value("Preset ownership is required."));
	}

	@Test
	void finalizeThumbnailUploadReturnsOkWithUpdatedPreset() throws Exception {
		Preset updatedPreset = new Preset(
				77L,
				"Aurora Drift",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						"""),
				"https://cdn.example.com/presets/15/thumbnails/new-thumb.png");
		ReflectionTestUtils.setField(updatedPreset, "id", 15L);
		ReflectionTestUtils.setField(updatedPreset, "createdAt", Instant.parse("2026-03-26T15:30:00Z"));

		when(this.presetService.finalizeThumbnailUpload(77L, 15L, "presets/15/thumbnails/new-thumb.png"))
				.thenReturn(updatedPreset);
		when(this.userRepository.findById(77L)).thenReturn(Optional.of(user(77L, "Preset Creator")));

		this.mockMvc.perform(post("/api/presets/15/thumbnail/finalize")
				.requestAttr(AuthenticatedUserRequest.USER_ID_ATTRIBUTE, 77L)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"objectKey":"presets/15/thumbnails/new-thumb.png"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.presetId").value(15L))
				.andExpect(jsonPath("$.creatorDisplayName").value("Preset Creator"))
				.andExpect(jsonPath("$.thumbnailRef").value("https://cdn.example.com/presets/15/thumbnails/new-thumb.png"));
	}

	@Test
	void finalizeThumbnailUploadReturnsNotFoundWhenPresetDoesNotExist() throws Exception {
		when(this.presetService.finalizeThumbnailUpload(77L, 99999L, "presets/99999/thumbnails/new-thumb.png"))
				.thenThrow(new PresetNotFoundException("Preset not found."));

		this.mockMvc.perform(post("/api/presets/99999/thumbnail/finalize")
				.requestAttr(AuthenticatedUserRequest.USER_ID_ATTRIBUTE, 77L)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"objectKey":"presets/99999/thumbnails/new-thumb.png"}
						"""))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PRESET_NOT_FOUND"))
				.andExpect(jsonPath("$.message").value("Preset not found."));
	}

	@Test
	void finalizeThumbnailUploadReturnsForbiddenWhenCallerIsNotOwner() throws Exception {
		when(this.presetService.finalizeThumbnailUpload(88L, 15L, "presets/15/thumbnails/new-thumb.png"))
				.thenThrow(new PresetOwnershipRequiredException("Preset ownership is required."));

		this.mockMvc.perform(post("/api/presets/15/thumbnail/finalize")
				.requestAttr(AuthenticatedUserRequest.USER_ID_ATTRIBUTE, 88L)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"objectKey":"presets/15/thumbnails/new-thumb.png"}
						"""))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("PRESET_OWNERSHIP_REQUIRED"))
				.andExpect(jsonPath("$.message").value("Preset ownership is required."));
	}

	private User user(Long userId, String displayName) {
		User user = new User("user-" + userId + "@example.com", "hashed-password", displayName);
		ReflectionTestUtils.setField(user, "id", userId);
		return user;
	}
}
