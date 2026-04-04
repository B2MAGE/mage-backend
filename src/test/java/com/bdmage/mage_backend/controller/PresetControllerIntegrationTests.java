package com.bdmage.mage_backend.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
import com.bdmage.mage_backend.support.PostgresIntegrationTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.FileSystemUtils;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PresetControllerIntegrationTests extends PostgresIntegrationTestSupport {

	private static final Path THUMBNAIL_UPLOAD_DIRECTORY = createUploadDirectory();

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

	@DynamicPropertySource
	static void registerThumbnailUploadProperties(DynamicPropertyRegistry registry) {
		registry.add("mage.storage.thumbnail.upload-dir", () -> THUMBNAIL_UPLOAD_DIRECTORY.toString());
	}

	@BeforeEach
	void resetThumbnailUploadDirectory() throws IOException {
		FileSystemUtils.deleteRecursively(THUMBNAIL_UPLOAD_DIRECTORY);
		Files.createDirectories(THUMBNAIL_UPLOAD_DIRECTORY);
	}

	@AfterAll
	static void cleanUpThumbnailUploadDirectory() throws IOException {
		FileSystemUtils.deleteRecursively(THUMBNAIL_UPLOAD_DIRECTORY);
	}

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
	void uploadThumbnailWritesFileToDiskAndPersistsFilenameForPresetOwner() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "upload-thumbnail-user-" + uniqueSuffix + "@example.com";
		String password = "password-" + uniqueSuffix;
		byte[] thumbnailBytes = "thumbnail-data".getBytes();

		User savedUser = this.userRepository.saveAndFlush(new User(
				email,
				this.passwordHashingService.hash(password),
				"Upload Thumbnail User"));
		Preset savedPreset = this.presetRepository.saveAndFlush(new Preset(
				savedUser.getId(),
				"Aurora Drift",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						""")));

		String accessToken = accessToken(this.mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequestBody(email, password)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn());

		MvcResult uploadResult = this.mockMvc.perform(multipart("/presets/" + savedPreset.getId() + "/thumbnail")
				.file(new MockMultipartFile("file", "aurora.png", "image/png", thumbnailBytes))
				.header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.presetId").value(savedPreset.getId()))
				.andExpect(jsonPath("$.thumbnailFilename").isNotEmpty())
				.andReturn();

		String thumbnailFilename = this.objectMapper.readTree(uploadResult.getResponse().getContentAsString())
				.path("thumbnailFilename")
				.asText();
		Path uploadedThumbnail = THUMBNAIL_UPLOAD_DIRECTORY.resolve(thumbnailFilename);

		assertThat(thumbnailFilename).startsWith("preset_" + savedPreset.getId() + "_").endsWith(".png");
		assertThat(thumbnailFilename).isNotEqualTo("aurora.png");
		assertThat(Files.exists(uploadedThumbnail)).isTrue();
		assertThat(Files.readAllBytes(uploadedThumbnail)).isEqualTo(thumbnailBytes);
		assertThat(this.presetRepository.findById(savedPreset.getId()).orElseThrow().getThumbnailRef())
				.isEqualTo(thumbnailFilename);
	}

	@Test
	void uploadThumbnailRejectsUnsupportedFileTypes() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "invalid-thumbnail-user-" + uniqueSuffix + "@example.com";
		String password = "password-" + uniqueSuffix;

		User savedUser = this.userRepository.saveAndFlush(new User(
				email,
				this.passwordHashingService.hash(password),
				"Invalid Thumbnail User"));
		Preset savedPreset = this.presetRepository.saveAndFlush(new Preset(
				savedUser.getId(),
				"Signal Bloom",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"pulse"}}
						""")));

		String accessToken = accessToken(this.mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequestBody(email, password)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn());

		this.mockMvc.perform(multipart("/presets/" + savedPreset.getId() + "/thumbnail")
				.file(new MockMultipartFile("file", "aurora.gif", "image/gif", "gif-data".getBytes()))
				.header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.code").value("UNSUPPORTED_THUMBNAIL_TYPE"))
				.andExpect(jsonPath("$.message").value("Supported thumbnail types are image/png, image/jpeg, and image/webp."));

		assertThat(this.presetRepository.findById(savedPreset.getId()).orElseThrow().getThumbnailRef()).isNull();
		assertUploadDirectoryEmpty();
	}

	@Test
	void uploadThumbnailReturnsNotFoundForNonexistentPreset() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "missing-thumbnail-preset-user-" + uniqueSuffix + "@example.com";
		String password = "password-" + uniqueSuffix;

		this.userRepository.saveAndFlush(new User(
				email,
				this.passwordHashingService.hash(password),
				"Missing Thumbnail Preset User"));

		String accessToken = accessToken(this.mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequestBody(email, password)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn());

		this.mockMvc.perform(multipart("/presets/99999/thumbnail")
				.file(new MockMultipartFile("file", "aurora.png", "image/png", "thumbnail-data".getBytes()))
				.header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PRESET_NOT_FOUND"))
				.andExpect(jsonPath("$.message").value("Preset not found."));

		assertUploadDirectoryEmpty();
	}

	@Test
	void uploadThumbnailReturnsForbiddenForNonOwner() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String ownerEmail = "thumbnail-owner-" + uniqueSuffix + "@example.com";
		String ownerPassword = "owner-password-" + uniqueSuffix;
		String otherEmail = "thumbnail-other-" + uniqueSuffix + "@example.com";
		String otherPassword = "other-password-" + uniqueSuffix;

		User ownerUser = this.userRepository.saveAndFlush(new User(
				ownerEmail,
				this.passwordHashingService.hash(ownerPassword),
				"Thumbnail Owner"));
		this.userRepository.saveAndFlush(new User(
				otherEmail,
				this.passwordHashingService.hash(otherPassword),
				"Thumbnail Other User"));
		Preset savedPreset = this.presetRepository.saveAndFlush(new Preset(
				ownerUser.getId(),
				"Polar Echo",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"glacier"}}
						""")));

		String otherUserAccessToken = accessToken(this.mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequestBody(otherEmail, otherPassword)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn());

		this.mockMvc.perform(multipart("/presets/" + savedPreset.getId() + "/thumbnail")
				.file(new MockMultipartFile("file", "aurora.png", "image/png", "thumbnail-data".getBytes()))
				.header("Authorization", "Bearer " + otherUserAccessToken))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("PRESET_FORBIDDEN"))
				.andExpect(jsonPath("$.message").value("Only the preset owner can upload or replace this preset thumbnail."));

		assertThat(this.presetRepository.findById(savedPreset.getId()).orElseThrow().getThumbnailRef()).isNull();
		assertUploadDirectoryEmpty();
	}

	@Test
	void getPresetsReturnsUnauthorizedWhenRequestHasNoAuthenticationHeader() throws Exception {
		this.mockMvc.perform(get("/presets")
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

		String accessToken = accessToken(this.mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequestBody(email, password)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn());

		this.mockMvc.perform(get("/presets")
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

		String accessToken = accessToken(this.mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequestBody(email, password)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn());

		this.mockMvc.perform(get("/presets")
				.header("Authorization", "Bearer " + accessToken)
				.param("tag", "ambient")
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0]").doesNotExist());
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

	private static Path createUploadDirectory() {
		try {
			return Files.createTempDirectory("mage-thumbnail-upload-tests");
		} catch (IOException ex) {
			throw new IllegalStateException("Failed to create a temporary upload directory for thumbnail integration tests.", ex);
		}
	}

	private static void assertUploadDirectoryEmpty() throws IOException {
		try (var uploadedFiles = Files.list(THUMBNAIL_UPLOAD_DIRECTORY)) {
			assertThat(uploadedFiles.toList()).isEmpty();
		}
	}
}
