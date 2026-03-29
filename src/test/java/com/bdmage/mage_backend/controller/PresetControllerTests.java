package com.bdmage.mage_backend.controller;

import java.time.Instant;

import com.bdmage.mage_backend.config.AuthenticatedUserRequest;
import com.bdmage.mage_backend.exception.ApiExceptionHandler;
import com.bdmage.mage_backend.exception.AuthenticationRequiredException;
import com.bdmage.mage_backend.exception.PresetNotFoundException;
import com.bdmage.mage_backend.exception.PresetTagAlreadyExistsException;
import com.bdmage.mage_backend.exception.TagNotFoundException;
import com.bdmage.mage_backend.model.Preset;
import com.bdmage.mage_backend.model.PresetTag;
import com.bdmage.mage_backend.service.PresetService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
