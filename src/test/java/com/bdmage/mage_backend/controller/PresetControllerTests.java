package com.bdmage.mage_backend.controller;

import java.time.Instant;
import java.util.List;

import com.bdmage.mage_backend.config.AuthenticatedUserRequest;
import com.bdmage.mage_backend.exception.ApiExceptionHandler;
import com.bdmage.mage_backend.exception.AuthenticationRequiredException;
import com.bdmage.mage_backend.exception.PresetAccessDeniedException;
import com.bdmage.mage_backend.exception.PresetNotFoundException;
import com.bdmage.mage_backend.exception.PresetTagAlreadyExistsException;
import com.bdmage.mage_backend.exception.TagNotFoundException;
import com.bdmage.mage_backend.exception.UnsupportedThumbnailContentTypeException;
import com.bdmage.mage_backend.model.Preset;
import com.bdmage.mage_backend.model.PresetTag;
import com.bdmage.mage_backend.service.PresetService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PresetControllerTests {

	private final ObjectMapper objectMapper = new ObjectMapper();

	private PresetService presetService;
	private MockMvc mockMvc;
	private LocalValidatorFactoryBean validator;

	@BeforeEach
	void setUp() {
		this.presetService = mock(PresetService.class);
		this.validator = new LocalValidatorFactoryBean();
		this.validator.afterPropertiesSet();
		this.mockMvc = MockMvcBuilders
				.standaloneSetup(new PresetController(this.presetService))
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
						"""),
				"thumbnails/preset-1.png");
		ReflectionTestUtils.setField(preset, "id", 15L);
		ReflectionTestUtils.setField(preset, "createdAt", Instant.parse("2026-03-26T15:30:00Z"));

		when(this.presetService.createPreset(
				77L,
				"Aurora Drift",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"},"state":{"energy":0.92}}
						"""),
				"thumbnails/preset-1.png"))
				.thenReturn(preset);

		this.mockMvc.perform(post("/presets")
				.requestAttr(AuthenticatedUserRequest.USER_ID_ATTRIBUTE, 77L)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "name":"Aurora Drift",
						  "sceneData":{"visualizer":{"shader":"nebula"},"state":{"energy":0.92}},
						  "thumbnailRef":"thumbnails/preset-1.png"
						}
						"""))
				.andExpect(status().isCreated())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.presetId").value(15L))
				.andExpect(jsonPath("$.ownerUserId").value(77L))
				.andExpect(jsonPath("$.name").value("Aurora Drift"))
				.andExpect(jsonPath("$.sceneData.visualizer.shader").value("nebula"))
				.andExpect(jsonPath("$.sceneData.state.energy").value(0.92))
				.andExpect(jsonPath("$.thumbnailRef").value("thumbnails/preset-1.png"))
				.andExpect(jsonPath("$.createdAt").value("2026-03-26T15:30:00Z"));
	}

	@Test
	void createPresetRejectsInvalidRequestBody() throws Exception {
		this.mockMvc.perform(post("/presets")
				.requestAttr(AuthenticatedUserRequest.USER_ID_ATTRIBUTE, 77L)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "name":" ",
						  "thumbnailRef":"%s"
						}
						""".formatted("x".repeat(513))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.details.name").value("name must not be blank"))
				.andExpect(jsonPath("$.details.sceneData").value("sceneData must not be null"))
				.andExpect(jsonPath("$.details.thumbnailRef").value("thumbnailRef must be at most 512 characters"));
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

		this.mockMvc.perform(post("/presets")
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

		this.mockMvc.perform(get("/presets"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$[0].presetId").value(15L))
				.andExpect(jsonPath("$[0].name").value("Aurora Drift"))
				.andExpect(jsonPath("$[1].presetId").value(16L))
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

		this.mockMvc.perform(get("/presets").param("tag", "ambient"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$[0].presetId").value(15L))
				.andExpect(jsonPath("$[0].name").value("Aurora Drift"));
	}

	@Test
	void getPresetsReturnsEmptyListWhenNoPresetsMatchTagFilter() throws Exception {
		when(this.presetService.getPresetsByTag("ambient")).thenReturn(List.of());

		this.mockMvc.perform(get("/presets").param("tag", "ambient"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(content().json("[]"));
	}

	@Test
	void attachTagToPresetReturnsCreatedAssociationForAuthenticatedUser() throws Exception {
		PresetTag presetTag = new PresetTag(15L, 7L);

		when(this.presetService.attachTagToPreset(77L, 15L, 7L))
				.thenReturn(presetTag);

		this.mockMvc.perform(post("/presets/15/tags")
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
	void attachTagToPresetRejectsInvalidRequestBody() throws Exception {
		this.mockMvc.perform(post("/presets/15/tags")
				.requestAttr(AuthenticatedUserRequest.USER_ID_ATTRIBUTE, 77L)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.details.tagId").value("tagId must not be null"));
	}

	@Test
	void attachTagToPresetReturnsUnauthorizedWhenRequestIsNotAuthenticated() throws Exception {
		when(this.presetService.attachTagToPreset(null, 15L, 7L))
				.thenThrow(new AuthenticationRequiredException("Authentication is required."));

		this.mockMvc.perform(post("/presets/15/tags")
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

		this.mockMvc.perform(post("/presets/15/tags")
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

		this.mockMvc.perform(post("/presets/15/tags")
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
	void uploadThumbnailReturnsStoredFilenameForPresetOwner() throws Exception {
		MockMultipartFile file = new MockMultipartFile(
				"file",
				"cover.png",
				"image/png",
				"thumbnail-data".getBytes());

		when(this.presetService.uploadThumbnail(eq(77L), eq(15L), any(MultipartFile.class)))
				.thenReturn("preset_15_abcdef.png");

		this.mockMvc.perform(multipart("/presets/15/thumbnail")
				.file(file)
				.requestAttr(AuthenticatedUserRequest.USER_ID_ATTRIBUTE, 77L))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.presetId").value(15L))
				.andExpect(jsonPath("$.thumbnailFilename").value("preset_15_abcdef.png"));

		ArgumentCaptor<MultipartFile> multipartFileCaptor = ArgumentCaptor.forClass(MultipartFile.class);
		verify(this.presetService).uploadThumbnail(eq(77L), eq(15L), multipartFileCaptor.capture());
		assertThat(multipartFileCaptor.getValue().getOriginalFilename()).isEqualTo("cover.png");
		assertThat(multipartFileCaptor.getValue().getContentType()).isEqualTo("image/png");
	}

	@Test
	void uploadThumbnailRejectsMissingFilePart() throws Exception {
		this.mockMvc.perform(multipart("/presets/15/thumbnail")
				.requestAttr(AuthenticatedUserRequest.USER_ID_ATTRIBUTE, 77L))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_THUMBNAIL_UPLOAD"))
				.andExpect(jsonPath("$.message").value("Thumbnail file is required."))
				.andExpect(jsonPath("$.details.file").value("file is required"));

		verifyNoInteractions(this.presetService);
	}

	@Test
	void uploadThumbnailReturnsForbiddenForNonOwner() throws Exception {
		when(this.presetService.uploadThumbnail(eq(77L), eq(15L), any(MultipartFile.class)))
				.thenThrow(new PresetAccessDeniedException("Only the preset owner can upload or replace this preset thumbnail."));

		this.mockMvc.perform(multipart("/presets/15/thumbnail")
				.file(new MockMultipartFile("file", "cover.png", "image/png", "thumbnail-data".getBytes()))
				.requestAttr(AuthenticatedUserRequest.USER_ID_ATTRIBUTE, 77L))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("PRESET_FORBIDDEN"))
				.andExpect(jsonPath("$.message").value("Only the preset owner can upload or replace this preset thumbnail."));
	}

	@Test
	void uploadThumbnailReturnsNotFoundWhenPresetDoesNotExist() throws Exception {
		when(this.presetService.uploadThumbnail(eq(77L), eq(99999L), any(MultipartFile.class)))
				.thenThrow(new PresetNotFoundException("Preset not found."));

		this.mockMvc.perform(multipart("/presets/99999/thumbnail")
				.file(new MockMultipartFile("file", "cover.png", "image/png", "thumbnail-data".getBytes()))
				.requestAttr(AuthenticatedUserRequest.USER_ID_ATTRIBUTE, 77L))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PRESET_NOT_FOUND"))
				.andExpect(jsonPath("$.message").value("Preset not found."));
	}

	@Test
	void uploadThumbnailReturnsUnsupportedMediaTypeForRejectedContentType() throws Exception {
		when(this.presetService.uploadThumbnail(eq(77L), eq(15L), any(MultipartFile.class)))
				.thenThrow(new UnsupportedThumbnailContentTypeException(
						"Supported thumbnail types are image/png, image/jpeg, and image/webp."));

		this.mockMvc.perform(multipart("/presets/15/thumbnail")
				.file(new MockMultipartFile("file", "cover.gif", "image/gif", "gif-data".getBytes()))
				.requestAttr(AuthenticatedUserRequest.USER_ID_ATTRIBUTE, 77L))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.code").value("UNSUPPORTED_THUMBNAIL_TYPE"))
				.andExpect(jsonPath("$.message").value("Supported thumbnail types are image/png, image/jpeg, and image/webp."));
	}

	@Test
	void getPresetReturnsPresetById() throws Exception {
		Preset preset = new Preset(
				77L,
				"Aurora Drift",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"},"state":{"energy":0.92}}
						"""),
				"thumbnails/preset-1.png");
		ReflectionTestUtils.setField(preset, "id", 15L);
		ReflectionTestUtils.setField(preset, "createdAt", Instant.parse("2026-03-26T15:30:00Z"));

		when(this.presetService.getPreset(15L)).thenReturn(preset);

		this.mockMvc.perform(get("/presets/15"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.presetId").value(15L))
				.andExpect(jsonPath("$.ownerUserId").value(77L))
				.andExpect(jsonPath("$.name").value("Aurora Drift"))
				.andExpect(jsonPath("$.sceneData.visualizer.shader").value("nebula"))
				.andExpect(jsonPath("$.sceneData.state.energy").value(0.92))
				.andExpect(jsonPath("$.thumbnailRef").value("thumbnails/preset-1.png"))
				.andExpect(jsonPath("$.createdAt").value("2026-03-26T15:30:00Z"));
	}

	@Test
	void getPresetReturnsNotFoundWhenPresetDoesNotExist() throws Exception {
		when(this.presetService.getPreset(99999L))
				.thenThrow(new PresetNotFoundException("Preset not found."));

		this.mockMvc.perform(get("/presets/99999"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PRESET_NOT_FOUND"))
				.andExpect(jsonPath("$.message").value("Preset not found."));
	}
}
