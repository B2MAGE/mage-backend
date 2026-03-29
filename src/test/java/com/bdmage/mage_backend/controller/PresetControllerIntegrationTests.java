package com.bdmage.mage_backend.controller;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.bdmage.mage_backend.model.Preset;
import com.bdmage.mage_backend.model.PresetTag;
import com.bdmage.mage_backend.model.Tag;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.repository.PresetRepository;
import com.bdmage.mage_backend.repository.PresetTagRepository;
import com.bdmage.mage_backend.repository.TagRepository;
import com.bdmage.mage_backend.repository.UserRepository;
import com.bdmage.mage_backend.service.PasswordHashingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bdmage.mage_backend.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
class PresetControllerIntegrationTests extends PostgresIntegrationTestSupport {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PresetRepository presetRepository;

	@Autowired
	private PresetTagRepository presetTagRepository;

	@Autowired
	private TagRepository tagRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordHashingService passwordHashingService;

	@Test
	void createPresetReturnsUnauthorizedWhenRequestHasNoAuthenticationHeader() throws Exception {
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
	void createPresetPersistsPresetForTokenAuthenticatedUser() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "preset-user-" + uniqueSuffix + "@example.com";
		String password = "password-" + uniqueSuffix;

		User savedUser = this.userRepository.saveAndFlush(new User(
				email,
				this.passwordHashingService.hash(password),
				"Preset User"));

		MvcResult loginResult = this.mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequestBody(email, password)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn();

		String accessToken = accessToken(loginResult);

		MvcResult createResult = this.mockMvc.perform(post("/presets")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "name":" Aurora Drift ",
						  "sceneData":{"visualizer":{"shader":"nebula"},"state":{"energy":0.92}},
						  "thumbnailRef":"  thumbnails/preset-1.png  "
						}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.ownerUserId").value(savedUser.getId()))
				.andExpect(jsonPath("$.name").value("Aurora Drift"))
				.andExpect(jsonPath("$.sceneData.visualizer.shader").value("nebula"))
				.andExpect(jsonPath("$.thumbnailRef").value("thumbnails/preset-1.png"))
				.andExpect(jsonPath("$.createdAt").isNotEmpty())
				.andReturn();

		Long presetId = presetId(createResult);
		Preset savedPreset = this.presetRepository.findById(presetId).orElseThrow();

		assertThat(savedPreset.getOwnerUserId()).isEqualTo(savedUser.getId());
		assertThat(savedPreset.getName()).isEqualTo("Aurora Drift");
		assertThat(savedPreset.getSceneData().path("visualizer").path("shader").asText()).isEqualTo("nebula");
		assertThat(savedPreset.getThumbnailRef()).isEqualTo("thumbnails/preset-1.png");
		assertThat(savedPreset.getCreatedAt()).isNotNull();
	}

	@Test
	void createPresetRejectsInvalidRequestBody() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "preset-validation-" + uniqueSuffix + "@example.com";
		String password = "password-" + uniqueSuffix;

		this.userRepository.saveAndFlush(new User(
				email,
				this.passwordHashingService.hash(password),
				"Preset Validation User"));

		MvcResult loginResult = this.mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequestBody(email, password)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn();

		String accessToken = accessToken(loginResult);

		this.mockMvc.perform(post("/presets")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "name":" ",
						  "thumbnailRef":"thumb"
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.details.name").value("name must not be blank"))
				.andExpect(jsonPath("$.details.sceneData").value("sceneData must not be null"));
	}

	@Test
	void attachTagToPresetReturnsUnauthorizedWhenRequestHasNoAuthenticationHeader() throws Exception {
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
	void attachTagToPresetPersistsAssociationForAuthenticatedUser() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "attach-tag-user-" + uniqueSuffix + "@example.com";
		String password = "password-" + uniqueSuffix;

		User savedUser = this.userRepository.saveAndFlush(new User(
				email,
				this.passwordHashingService.hash(password),
				"Attach Tag User"));
		Preset savedPreset = this.presetRepository.saveAndFlush(new Preset(
				savedUser.getId(),
				"Aurora Drift",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						""")));
		Tag savedTag = this.tagRepository.saveAndFlush(new Tag("ambient"));

		String accessToken = accessToken(this.mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequestBody(email, password)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn());

		this.mockMvc.perform(post("/presets/" + savedPreset.getId() + "/tags")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"tagId":%d}
						""".formatted(savedTag.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.presetId").value(savedPreset.getId()))
				.andExpect(jsonPath("$.tagId").value(savedTag.getId()));

		assertThat(this.presetTagRepository.findAllByPresetId(savedPreset.getId()))
				.extracting(PresetTag::getTagId)
				.containsExactly(savedTag.getId());
	}

	@Test
	void attachTagToPresetReturnsConflictForDuplicateAssociation() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "duplicate-attach-tag-user-" + uniqueSuffix + "@example.com";
		String password = "password-" + uniqueSuffix;

		User savedUser = this.userRepository.saveAndFlush(new User(
				email,
				this.passwordHashingService.hash(password),
				"Duplicate Attach Tag User"));
		Preset savedPreset = this.presetRepository.saveAndFlush(new Preset(
				savedUser.getId(),
				"Signal Bloom",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"pulse"}}
						""")));
		Tag savedTag = this.tagRepository.saveAndFlush(new Tag("showcase"));
		this.presetTagRepository.saveAndFlush(new PresetTag(savedPreset.getId(), savedTag.getId()));

		String accessToken = accessToken(this.mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequestBody(email, password)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn());

		this.mockMvc.perform(post("/presets/" + savedPreset.getId() + "/tags")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"tagId":%d}
						""".formatted(savedTag.getId())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PRESET_TAG_ALREADY_EXISTS"))
				.andExpect(jsonPath("$.message").value("This tag is already attached to the preset."));

		assertThat(this.presetTagRepository.findAllByPresetId(savedPreset.getId())).hasSize(1);
	}

	@Test
	void attachTagToPresetReturnsNotFoundWhenTagDoesNotExist() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "missing-tag-user-" + uniqueSuffix + "@example.com";
		String password = "password-" + uniqueSuffix;

		User savedUser = this.userRepository.saveAndFlush(new User(
				email,
				this.passwordHashingService.hash(password),
				"Missing Tag User"));
		Preset savedPreset = this.presetRepository.saveAndFlush(new Preset(
				savedUser.getId(),
				"Polar Echo",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"glacier"}}
						""")));

		String accessToken = accessToken(this.mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequestBody(email, password)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn());

		this.mockMvc.perform(post("/presets/" + savedPreset.getId() + "/tags")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"tagId":99999}
						"""))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("TAG_NOT_FOUND"))
				.andExpect(jsonPath("$.message").value("Tag not found."));
	}

	@Test
	void getPresetReturnsUnauthorizedWhenRequestHasNoAuthenticationHeader() throws Exception {
		this.mockMvc.perform(get("/presets/99999")
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
				.andExpect(jsonPath("$.message").value("Authentication is required."));
	}

	@Test
	void getPresetReturnsPresetWithAllFieldsForAuthenticatedUser() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "get-preset-user-" + uniqueSuffix + "@example.com";
		String password = "password-" + uniqueSuffix;

		User savedUser = this.userRepository.saveAndFlush(new User(
				email,
				this.passwordHashingService.hash(password),
				"Get Preset User"));

		String accessToken = accessToken(this.mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequestBody(email, password)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn());

		Preset savedPreset = this.presetRepository.saveAndFlush(new Preset(
				savedUser.getId(),
				"Aurora Drift",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"},"state":{"energy":0.92}}
						"""),
				"thumbnails/preset-1.png"));

		this.mockMvc.perform(get("/presets/" + savedPreset.getId())
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.presetId").value(savedPreset.getId()))
				.andExpect(jsonPath("$.ownerUserId").value(savedUser.getId()))
				.andExpect(jsonPath("$.name").value("Aurora Drift"))
				.andExpect(jsonPath("$.sceneData.visualizer.shader").value("nebula"))
				.andExpect(jsonPath("$.sceneData.state.energy").value(0.92))
				.andExpect(jsonPath("$.thumbnailRef").value("thumbnails/preset-1.png"))
				.andExpect(jsonPath("$.createdAt").isNotEmpty());
	}

	@Test
	void getPresetReturnsNotFoundForNonexistentPreset() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "missing-preset-user-" + uniqueSuffix + "@example.com";
		String password = "password-" + uniqueSuffix;

		this.userRepository.saveAndFlush(new User(
				email,
				this.passwordHashingService.hash(password),
				"Missing Preset User"));

		String accessToken = accessToken(this.mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequestBody(email, password)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn());

		this.mockMvc.perform(get("/presets/99999")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PRESET_NOT_FOUND"))
				.andExpect(jsonPath("$.message").value("Preset not found."));
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

	private static Long presetId(MvcResult result) throws Exception {
		Matcher matcher = Pattern.compile("\"presetId\":(\\d+)")
				.matcher(result.getResponse().getContentAsString());
		assertThat(matcher.find()).isTrue();
		return Long.valueOf(matcher.group(1));
	}
}
