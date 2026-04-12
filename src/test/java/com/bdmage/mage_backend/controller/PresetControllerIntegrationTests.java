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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
	void createPresetPersistsPresetForTokenAuthenticatedUser() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "preset-user-" + uniqueSuffix + "@example.com";
		String password = "password-" + uniqueSuffix;

		User savedUser = this.userRepository.saveAndFlush(new User(
				email,
				this.passwordHashingService.hash(password),
				"Preset User"));

		MvcResult loginResult = this.mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequestBody(email, password)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn();

		String accessToken = accessToken(loginResult);

		MvcResult createResult = this.mockMvc.perform(post("/api/presets")
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

		MvcResult loginResult = this.mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequestBody(email, password)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn();

		String accessToken = accessToken(loginResult);

		this.mockMvc.perform(post("/api/presets")
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
	void attachTagToPresetPersistsAssociationForAuthenticatedUser() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "attach-tag-user-" + uniqueSuffix + "@example.com";
		String password = "password-" + uniqueSuffix;
		String tagName = "ambient-" + uniqueSuffix;

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
		Tag savedTag = this.tagRepository.saveAndFlush(new Tag(tagName));

		String accessToken = accessToken(this.mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequestBody(email, password)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn());

		this.mockMvc.perform(post("/api/presets/" + savedPreset.getId() + "/tags")
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
		String tagName = "showcase-" + uniqueSuffix;

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
		Tag savedTag = this.tagRepository.saveAndFlush(new Tag(tagName));
		this.presetTagRepository.saveAndFlush(new PresetTag(savedPreset.getId(), savedTag.getId()));

		String accessToken = accessToken(this.mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequestBody(email, password)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn());

		this.mockMvc.perform(post("/api/presets/" + savedPreset.getId() + "/tags")
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

		String accessToken = accessToken(this.mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequestBody(email, password)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn());

		this.mockMvc.perform(post("/api/presets/" + savedPreset.getId() + "/tags")
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
	void getPresetsReturnsUnauthorizedWhenRequestHasNoAuthenticationHeader() throws Exception {
		this.mockMvc.perform(get("/api/presets")
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
				.andExpect(jsonPath("$.message").value("Authentication is required."));
	}

	@Test
	void getPresetsFiltersByTagForAuthenticatedUser() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "filter-presets-user-" + uniqueSuffix + "@example.com";
		String password = "password-" + uniqueSuffix;
		String ambientTagName = "ambient-" + uniqueSuffix;
		String showcaseTagName = "showcase-" + uniqueSuffix;

		User savedUser = this.userRepository.saveAndFlush(new User(
				email,
				this.passwordHashingService.hash(password),
				"Filter Presets User"));
		Preset ambientPreset = this.presetRepository.saveAndFlush(new Preset(
				savedUser.getId(),
				"Aurora Drift",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						""")));
		Preset otherPreset = this.presetRepository.saveAndFlush(new Preset(
				savedUser.getId(),
				"Signal Bloom",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"pulse"}}
						""")));
		Tag ambientTag = this.tagRepository.saveAndFlush(new Tag(ambientTagName));
		Tag showcaseTag = this.tagRepository.saveAndFlush(new Tag(showcaseTagName));
		this.presetTagRepository.saveAndFlush(new PresetTag(ambientPreset.getId(), ambientTag.getId()));
		this.presetTagRepository.saveAndFlush(new PresetTag(otherPreset.getId(), showcaseTag.getId()));

		String accessToken = accessToken(this.mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequestBody(email, password)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn());

		this.mockMvc.perform(get("/api/presets")
				.header("Authorization", "Bearer " + accessToken)
				.param("tag", " " + ambientTagName.toUpperCase() + " ")
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].presetId").value(ambientPreset.getId()))
				.andExpect(jsonPath("$[0].name").value("Aurora Drift"))
				.andExpect(jsonPath("$[1]").doesNotExist());
	}

	@Test
	void getPresetsReturnsEmptyListWhenNoPresetsMatchTagFilter() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "empty-filter-presets-user-" + uniqueSuffix + "@example.com";
		String password = "password-" + uniqueSuffix;

		this.userRepository.saveAndFlush(new User(
				email,
				this.passwordHashingService.hash(password),
				"Empty Filter Presets User"));

		String accessToken = accessToken(this.mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequestBody(email, password)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn());

		this.mockMvc.perform(get("/api/presets")
				.header("Authorization", "Bearer " + accessToken)
				.param("tag", "ambient")
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0]").doesNotExist());
	}

	@Test
	void getPresetReturnsPresetWithAllFieldsForPublicRequest() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "public-get-preset-user-" + uniqueSuffix + "@example.com";

		User savedUser = this.userRepository.saveAndFlush(new User(
				email,
				this.passwordHashingService.hash("unused-password-" + uniqueSuffix),
				"Public Get Preset User"));

		Preset savedPreset = this.presetRepository.saveAndFlush(new Preset(
				savedUser.getId(),
				"Aurora Drift",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"},"state":{"energy":0.92}}
						"""),
				"thumbnails/preset-1.png"));

		this.mockMvc.perform(get("/api/presets/" + savedPreset.getId())
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
	void getPresetReturnsPresetWithAllFieldsForAuthenticatedUser() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "get-preset-user-" + uniqueSuffix + "@example.com";
		String password = "password-" + uniqueSuffix;

		User savedUser = this.userRepository.saveAndFlush(new User(
				email,
				this.passwordHashingService.hash(password),
				"Get Preset User"));

		String accessToken = accessToken(this.mockMvc.perform(post("/api/auth/login")
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

		this.mockMvc.perform(get("/api/presets/" + savedPreset.getId())
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
	void getPresetReturnsNotFoundForPublicRequestWhenPresetDoesNotExist() throws Exception {
		this.mockMvc.perform(get("/api/presets/99999")
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PRESET_NOT_FOUND"))
				.andExpect(jsonPath("$.message").value("Preset not found."));
	}

	@Test
	void deletePresetReturnsUnauthorizedWhenRequestHasNoAuthenticationHeader() throws Exception {
		this.mockMvc.perform(delete("/api/presets/15")
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
				.andExpect(jsonPath("$.message").value("Authentication is required."));
	}

	@Test
	void deletePresetDeletesOwnedPresetAndRemovesItFromSubsequentReads() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "delete-preset-owner-" + uniqueSuffix + "@example.com";
		String password = "password-" + uniqueSuffix;
		String tagName = "delete-tag-" + uniqueSuffix;

		User savedUser = this.userRepository.saveAndFlush(new User(
				email,
				this.passwordHashingService.hash(password),
				"Delete Preset Owner"));
		Preset savedPreset = this.presetRepository.saveAndFlush(new Preset(
				savedUser.getId(),
				"Aurora Drift",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						""")));
		Tag savedTag = this.tagRepository.saveAndFlush(new Tag(tagName));
		this.presetTagRepository.saveAndFlush(new PresetTag(savedPreset.getId(), savedTag.getId()));

		String accessToken = accessToken(this.mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequestBody(email, password)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn());

		this.mockMvc.perform(delete("/api/presets/" + savedPreset.getId())
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isNoContent());

		assertThat(this.presetRepository.findById(savedPreset.getId())).isEmpty();
		assertThat(this.presetTagRepository.findAllByPresetId(savedPreset.getId())).isEmpty();

		this.mockMvc.perform(get("/api/presets/" + savedPreset.getId())
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PRESET_NOT_FOUND"))
				.andExpect(jsonPath("$.message").value("Preset not found."));

		this.mockMvc.perform(get("/api/users/" + savedUser.getId() + "/presets")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0]").doesNotExist());
	}

	@Test
	void deletePresetReturnsForbiddenWhenAuthenticatedUserDoesNotOwnPreset() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String ownerEmail = "delete-owner-" + uniqueSuffix + "@example.com";
		String ownerPassword = "owner-password-" + uniqueSuffix;
		String otherEmail = "delete-other-" + uniqueSuffix + "@example.com";
		String otherPassword = "other-password-" + uniqueSuffix;

		User owner = this.userRepository.saveAndFlush(new User(
				ownerEmail,
				this.passwordHashingService.hash(ownerPassword),
				"Delete Owner"));
		this.userRepository.saveAndFlush(new User(
				otherEmail,
				this.passwordHashingService.hash(otherPassword),
				"Delete Other User"));
		Preset savedPreset = this.presetRepository.saveAndFlush(new Preset(
				owner.getId(),
				"Signal Bloom",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"pulse"}}
						""")));

		String accessToken = accessToken(this.mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequestBody(otherEmail, otherPassword)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn());

		this.mockMvc.perform(delete("/api/presets/" + savedPreset.getId())
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("PRESET_FORBIDDEN"))
				.andExpect(jsonPath("$.message").value("You do not have permission to delete this preset."));

		assertThat(this.presetRepository.findById(savedPreset.getId())).isPresent();
	}

	@Test
	void deletePresetReturnsNotFoundForNonexistentPreset() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "delete-missing-preset-" + uniqueSuffix + "@example.com";
		String password = "password-" + uniqueSuffix;

		this.userRepository.saveAndFlush(new User(
				email,
				this.passwordHashingService.hash(password),
				"Delete Missing Preset User"));

		String accessToken = accessToken(this.mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequestBody(email, password)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn());

		this.mockMvc.perform(delete("/api/presets/99999")
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


