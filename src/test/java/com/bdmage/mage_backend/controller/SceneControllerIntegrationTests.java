package com.bdmage.mage_backend.controller;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.bdmage.mage_backend.model.Playlist;
import com.bdmage.mage_backend.model.Scene;
import com.bdmage.mage_backend.model.SceneTag;
import com.bdmage.mage_backend.model.Tag;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.repository.PlaylistRepository;
import com.bdmage.mage_backend.repository.SceneRepository;
import com.bdmage.mage_backend.repository.ScenePlaylistRepository;
import com.bdmage.mage_backend.repository.SceneTagRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class SceneControllerIntegrationTests extends PostgresIntegrationTestSupport {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private SceneRepository sceneRepository;

	@Autowired
	private SceneTagRepository sceneTagRepository;

	@Autowired
	private ScenePlaylistRepository scenePlaylistRepository;

	@Autowired
	private PlaylistRepository playlistRepository;

	@Autowired
	private TagRepository tagRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordHashingService passwordHashingService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void createSceneReturnsUnauthorizedWhenRequestHasNoAuthenticationHeader() throws Exception {
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
	void createScenePersistsSceneForTokenAuthenticatedUser() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "scene-user-" + uniqueSuffix + "@example.com";
		String password = "password-" + uniqueSuffix;

		User savedUser = this.userRepository.saveAndFlush(new User(
				email,
				this.passwordHashingService.hash(password),
				"Scene User"));

		MvcResult loginResult = this.mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequestBody(email, password)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn();

		String accessToken = accessToken(loginResult);

		MvcResult createResult = this.mockMvc.perform(post("/api/scenes")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "name":" Aurora Drift ",
						  "description":" Soft teal bloom with low-end drift. ",
						  "sceneData":{"visualizer":{"shader":"nebula"},"state":{"energy":0.92}}
						}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.ownerUserId").value(savedUser.getId()))
				.andExpect(jsonPath("$.creatorDisplayName").value("Scene User"))
				.andExpect(jsonPath("$.name").value("Aurora Drift"))
				.andExpect(jsonPath("$.description").value("Soft teal bloom with low-end drift."))
				.andExpect(jsonPath("$.sceneData.visualizer.shader").value("nebula"))
				.andExpect(jsonPath("$.createdAt").isNotEmpty())
				.andReturn();

		Long sceneId = sceneId(createResult);
		Scene savedScene = this.sceneRepository.findById(sceneId).orElseThrow();

		assertThat(savedScene.getOwnerUserId()).isEqualTo(savedUser.getId());
		assertThat(savedScene.getName()).isEqualTo("Aurora Drift");
		assertThat(savedScene.getDescription()).isEqualTo("Soft teal bloom with low-end drift.");
		assertThat(savedScene.getSceneData().path("visualizer").path("shader").asText()).isEqualTo("nebula");
		assertThat(savedScene.getThumbnailRef()).isNull();
		assertThat(savedScene.getCreatedAt()).isNotNull();
		assertThat(this.scenePlaylistRepository.findAllBySceneId(sceneId)).isEmpty();
	}

	@Test
	void createSceneWithPlaylistIdPersistsScenePlaylistMembership() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "scene-playlist-user-" + uniqueSuffix + "@example.com";
		String password = "password-" + uniqueSuffix;

		User savedUser = this.userRepository.saveAndFlush(new User(
				email,
				this.passwordHashingService.hash(password),
				"Scene Playlist User"));
		Playlist playlist = this.playlistRepository.saveAndFlush(new Playlist(savedUser.getId(), "Favorites"));

		String accessToken = accessToken(this.mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequestBody(email, password)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn());

		MvcResult createResult = this.mockMvc.perform(post("/api/scenes")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "name":" Playlist Scene ",
						  "sceneData":{"visualizer":{"shader":"nebula"}},
						  "playlistId":%d
						}
						""".formatted(playlist.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.ownerUserId").value(savedUser.getId()))
				.andExpect(jsonPath("$.name").value("Playlist Scene"))
				.andReturn();

		Long sceneId = sceneId(createResult);

		assertThat(this.scenePlaylistRepository.countBySceneIdAndPlaylistId(sceneId, playlist.getId()))
				.isEqualTo(1L);
	}

	@Test
	void createSceneWithAnotherUsersPlaylistIdReturnsNotFoundAndRollsBackScene() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "scene-playlist-owner-" + uniqueSuffix + "@example.com";
		String password = "password-" + uniqueSuffix;

		User savedUser = this.userRepository.saveAndFlush(new User(
				email,
				this.passwordHashingService.hash(password),
				"Scene Playlist Owner"));
		User otherUser = this.userRepository.saveAndFlush(new User(
				"scene-playlist-other-" + uniqueSuffix + "@example.com",
				this.passwordHashingService.hash("other-password-" + uniqueSuffix),
				"Other Playlist Owner"));
		Playlist otherPlaylist = this.playlistRepository.saveAndFlush(new Playlist(otherUser.getId(), "Not Yours"));

		String accessToken = accessToken(this.mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequestBody(email, password)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn());

		this.mockMvc.perform(post("/api/scenes")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "name":"Cross User Playlist Scene",
						  "sceneData":{"visualizer":{"shader":"nebula"}},
						  "playlistId":%d
						}
						""".formatted(otherPlaylist.getId())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PLAYLIST_NOT_FOUND"))
				.andExpect(jsonPath("$.message").value("Playlist not found."));

		assertThat(this.sceneRepository.findAllByOwnerUserId(savedUser.getId()))
				.extracting(Scene::getName)
				.doesNotContain("Cross User Playlist Scene");
	}

	@Test
	void createSceneRejectsOversizedDescription() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "scene-description-validation-" + uniqueSuffix + "@example.com";
		String password = "password-" + uniqueSuffix;

		this.userRepository.saveAndFlush(new User(
				email,
				this.passwordHashingService.hash(password),
				"Scene Description Validation User"));

		MvcResult loginResult = this.mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequestBody(email, password)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn();

		String accessToken = accessToken(loginResult);
		String requestBody = this.objectMapper.writeValueAsString(java.util.Map.of(
				"name", "Aurora Drift",
				"description", "a".repeat(1001),
				"sceneData", java.util.Map.of("visualizer", java.util.Map.of("shader", "nebula"))));

		this.mockMvc.perform(post("/api/scenes")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.details.description").value("description must be at most 1000 characters"));
	}

	@Test
	void updateSceneDescriptionAddsEditsAndClearsDescriptionForOwner() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "description-owner-" + uniqueSuffix + "@example.com";
		String password = "password-" + uniqueSuffix;

		User savedUser = this.userRepository.saveAndFlush(new User(
				email,
				this.passwordHashingService.hash(password),
				"Description Owner"));
		Scene savedScene = this.sceneRepository.saveAndFlush(new Scene(
				savedUser.getId(),
				"Aurora Drift",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						""")));

		String accessToken = accessToken(this.mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequestBody(email, password)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn());

		this.mockMvc.perform(patch("/api/scenes/" + savedScene.getId() + "/description")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"description":" Added from My Scenes. "}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sceneId").value(savedScene.getId()))
				.andExpect(jsonPath("$.description").value("Added from My Scenes."));

		assertThat(this.sceneRepository.findById(savedScene.getId()).orElseThrow().getDescription())
				.isEqualTo("Added from My Scenes.");

		this.mockMvc.perform(patch("/api/scenes/" + savedScene.getId() + "/description")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"description":"Edited from My Scenes."}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.description").value("Edited from My Scenes."));

		assertThat(this.sceneRepository.findById(savedScene.getId()).orElseThrow().getDescription())
				.isEqualTo("Edited from My Scenes.");

		this.mockMvc.perform(patch("/api/scenes/" + savedScene.getId() + "/description")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"description":"   "}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.description").doesNotExist());

		assertThat(this.sceneRepository.findById(savedScene.getId()).orElseThrow().getDescription())
				.isNull();
	}

	@Test
	void updateSceneDescriptionRejectsUnauthenticatedRequest() throws Exception {
		this.mockMvc.perform(patch("/api/scenes/15/description")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"description":"Updated from My Scenes."}
						"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
				.andExpect(jsonPath("$.message").value("Authentication is required."));
	}

	@Test
	void updateSceneDescriptionRejectsNonOwner() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String ownerEmail = "description-owner-only-" + uniqueSuffix + "@example.com";
		String otherEmail = "description-other-" + uniqueSuffix + "@example.com";
		String otherPassword = "password-" + uniqueSuffix;

		User owner = this.userRepository.saveAndFlush(new User(
				ownerEmail,
				this.passwordHashingService.hash("unused-password-" + uniqueSuffix),
				"Description Owner Only"));
		this.userRepository.saveAndFlush(new User(
				otherEmail,
				this.passwordHashingService.hash(otherPassword),
				"Description Other User"));
		Scene savedScene = this.sceneRepository.saveAndFlush(new Scene(
				owner.getId(),
				"Aurora Drift",
				"Original description.",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						""")));

		String accessToken = accessToken(this.mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequestBody(otherEmail, otherPassword)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn());

		this.mockMvc.perform(patch("/api/scenes/" + savedScene.getId() + "/description")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"description":"Unauthorized update."}
						"""))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("SCENE_OWNERSHIP_REQUIRED"))
				.andExpect(jsonPath("$.message").value("Scene ownership is required."));

		assertThat(this.sceneRepository.findById(savedScene.getId()).orElseThrow().getDescription())
				.isEqualTo("Original description.");
	}

	@Test
	void updateSceneDescriptionRejectsOversizedDescription() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "description-too-long-" + uniqueSuffix + "@example.com";
		String password = "password-" + uniqueSuffix;

		User savedUser = this.userRepository.saveAndFlush(new User(
				email,
				this.passwordHashingService.hash(password),
				"Description Too Long User"));
		Scene savedScene = this.sceneRepository.saveAndFlush(new Scene(
				savedUser.getId(),
				"Aurora Drift",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						""")));

		String accessToken = accessToken(this.mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequestBody(email, password)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn());
		String requestBody = this.objectMapper.writeValueAsString(java.util.Map.of(
				"description", "a".repeat(1001)));

		this.mockMvc.perform(patch("/api/scenes/" + savedScene.getId() + "/description")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.details.description").value("description must be at most 1000 characters"));
	}

	@Test
	void updateSceneAndTagRoutesPersistOwnerChanges() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "scene-update-owner-" + uniqueSuffix + "@example.com";
		String password = "password-" + uniqueSuffix;

		User savedUser = this.userRepository.saveAndFlush(new User(
				email,
				this.passwordHashingService.hash(password),
				"Scene Update Owner"));
		Scene savedScene = this.sceneRepository.saveAndFlush(new Scene(
				savedUser.getId(),
				"Aurora Drift",
				"Original description.",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						""")));
		Tag ambientTag = this.tagRepository.saveAndFlush(new Tag("ambient-update-" + uniqueSuffix));
		Tag showcaseTag = this.tagRepository.saveAndFlush(new Tag("showcase-update-" + uniqueSuffix));
		Tag focusTag = this.tagRepository.saveAndFlush(new Tag("focus-update-" + uniqueSuffix));
		this.sceneTagRepository.saveAndFlush(new SceneTag(savedScene.getId(), ambientTag.getId()));
		this.sceneTagRepository.saveAndFlush(new SceneTag(savedScene.getId(), showcaseTag.getId()));

		String accessToken = accessToken(this.mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequestBody(email, password)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn());

		this.mockMvc.perform(put("/api/scenes/" + savedScene.getId())
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "name":" Updated Scene ",
						  "description":" Updated description. ",
						  "sceneData":{"visualizer":{"shader":"pulse"},"state":{"energy":0.5}}
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sceneId").value(savedScene.getId()))
				.andExpect(jsonPath("$.name").value("Updated Scene"))
				.andExpect(jsonPath("$.description").value("Updated description."))
				.andExpect(jsonPath("$.sceneData.visualizer.shader").value("pulse"))
				.andExpect(jsonPath("$.sceneData.state.energy").value(0.5));

		Scene updatedScene = this.sceneRepository.findById(savedScene.getId()).orElseThrow();
		assertThat(updatedScene.getName()).isEqualTo("Updated Scene");
		assertThat(updatedScene.getDescription()).isEqualTo("Updated description.");
		assertThat(updatedScene.getSceneData().path("visualizer").path("shader").asText()).isEqualTo("pulse");

		this.mockMvc.perform(put("/api/scenes/" + savedScene.getId() + "/tags")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"tagIds":[%d]}
						""".formatted(focusTag.getId())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].sceneId").value(savedScene.getId()))
				.andExpect(jsonPath("$[0].tagId").value(focusTag.getId()))
				.andExpect(jsonPath("$[1]").doesNotExist());

		assertThat(this.sceneTagRepository.findAllBySceneId(savedScene.getId()))
				.extracting(SceneTag::getTagId)
				.containsExactly(focusTag.getId());

		this.mockMvc.perform(delete("/api/scenes/" + savedScene.getId() + "/tags/" + focusTag.getId())
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isNoContent());

		assertThat(this.sceneTagRepository.findAllBySceneId(savedScene.getId())).isEmpty();
	}

	@Test
	void updateSceneRejectsNonOwner() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String ownerEmail = "scene-update-owner-only-" + uniqueSuffix + "@example.com";
		String otherEmail = "scene-update-other-" + uniqueSuffix + "@example.com";
		String otherPassword = "password-" + uniqueSuffix;

		User owner = this.userRepository.saveAndFlush(new User(
				ownerEmail,
				this.passwordHashingService.hash("unused-password-" + uniqueSuffix),
				"Scene Update Owner Only"));
		this.userRepository.saveAndFlush(new User(
				otherEmail,
				this.passwordHashingService.hash(otherPassword),
				"Scene Update Other"));
		Scene savedScene = this.sceneRepository.saveAndFlush(new Scene(
				owner.getId(),
				"Aurora Drift",
				"Original description.",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						""")));

		String accessToken = accessToken(this.mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequestBody(otherEmail, otherPassword)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn());

		this.mockMvc.perform(put("/api/scenes/" + savedScene.getId())
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "name":"Unauthorized update",
						  "description":"Unauthorized description.",
						  "sceneData":{"visualizer":{"shader":"pulse"}}
						}
						"""))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("SCENE_OWNERSHIP_REQUIRED"))
				.andExpect(jsonPath("$.message").value("Scene ownership is required."));

		Scene unchangedScene = this.sceneRepository.findById(savedScene.getId()).orElseThrow();
		assertThat(unchangedScene.getName()).isEqualTo("Aurora Drift");
		assertThat(unchangedScene.getDescription()).isEqualTo("Original description.");
		assertThat(unchangedScene.getSceneData().path("visualizer").path("shader").asText()).isEqualTo("nebula");
	}

	@Test
	void createSceneRejectsInvalidRequestBody() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "scene-validation-" + uniqueSuffix + "@example.com";
		String password = "password-" + uniqueSuffix;

		this.userRepository.saveAndFlush(new User(
				email,
				this.passwordHashingService.hash(password),
				"Scene Validation User"));

		MvcResult loginResult = this.mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequestBody(email, password)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn();

		String accessToken = accessToken(loginResult);

		this.mockMvc.perform(post("/api/scenes")
				.header("Authorization", "Bearer " + accessToken)
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
	void attachTagToSceneReturnsUnauthorizedWhenRequestHasNoAuthenticationHeader() throws Exception {
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
	void attachTagToScenePersistsAssociationForAuthenticatedUser() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "attach-tag-user-" + uniqueSuffix + "@example.com";
		String password = "password-" + uniqueSuffix;
		String tagName = "ambient-" + uniqueSuffix;

		User savedUser = this.userRepository.saveAndFlush(new User(
				email,
				this.passwordHashingService.hash(password),
				"Attach Tag User"));
		Scene savedScene = this.sceneRepository.saveAndFlush(new Scene(
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

		this.mockMvc.perform(post("/api/scenes/" + savedScene.getId() + "/tags")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"tagId":%d}
						""".formatted(savedTag.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.sceneId").value(savedScene.getId()))
				.andExpect(jsonPath("$.tagId").value(savedTag.getId()));

		assertThat(this.sceneTagRepository.findAllBySceneId(savedScene.getId()))
				.extracting(SceneTag::getTagId)
				.containsExactly(savedTag.getId());
	}

	@Test
	void attachTagToSceneReturnsConflictForDuplicateAssociation() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "duplicate-attach-tag-user-" + uniqueSuffix + "@example.com";
		String password = "password-" + uniqueSuffix;
		String tagName = "showcase-" + uniqueSuffix;

		User savedUser = this.userRepository.saveAndFlush(new User(
				email,
				this.passwordHashingService.hash(password),
				"Duplicate Attach Tag User"));
		Scene savedScene = this.sceneRepository.saveAndFlush(new Scene(
				savedUser.getId(),
				"Signal Bloom",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"pulse"}}
						""")));
		Tag savedTag = this.tagRepository.saveAndFlush(new Tag(tagName));
		this.sceneTagRepository.saveAndFlush(new SceneTag(savedScene.getId(), savedTag.getId()));

		String accessToken = accessToken(this.mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequestBody(email, password)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn());

		this.mockMvc.perform(post("/api/scenes/" + savedScene.getId() + "/tags")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"tagId":%d}
						""".formatted(savedTag.getId())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SCENE_TAG_ALREADY_EXISTS"))
				.andExpect(jsonPath("$.message").value("This tag is already attached to the scene."));

		assertThat(this.sceneTagRepository.findAllBySceneId(savedScene.getId())).hasSize(1);
	}

	@Test
	void attachTagToSceneReturnsNotFoundWhenTagDoesNotExist() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "missing-tag-user-" + uniqueSuffix + "@example.com";
		String password = "password-" + uniqueSuffix;

		User savedUser = this.userRepository.saveAndFlush(new User(
				email,
				this.passwordHashingService.hash(password),
				"Missing Tag User"));
		Scene savedScene = this.sceneRepository.saveAndFlush(new Scene(
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

		this.mockMvc.perform(post("/api/scenes/" + savedScene.getId() + "/tags")
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
	void getScenesReturnsAllScenesForPublicRequest() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "public-list-scenes-user-" + uniqueSuffix + "@example.com";

		User savedUser = this.userRepository.saveAndFlush(new User(
				email,
				this.passwordHashingService.hash("unused-password-" + uniqueSuffix),
				"Public List Scenes User"));
		Scene firstScene = this.sceneRepository.saveAndFlush(new Scene(
				savedUser.getId(),
				"Aurora Drift",
				"Soft teal bloom with low-end drift.",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						""")));
		Scene secondScene = this.sceneRepository.saveAndFlush(new Scene(
				savedUser.getId(),
				"Signal Bloom",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"pulse"}}
						""")));

		this.mockMvc.perform(get("/api/scenes")
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].sceneId", hasItems(
						firstScene.getId().intValue(),
						secondScene.getId().intValue())))
				.andExpect(jsonPath("$[*].creatorDisplayName", hasItems(
						"Public List Scenes User",
						"Public List Scenes User")))
				.andExpect(jsonPath("$[*].name", hasItems("Aurora Drift", "Signal Bloom")))
				.andExpect(jsonPath("$[*].description", hasItems("Soft teal bloom with low-end drift.")));
	}

	@Test
	void getScenesFiltersByTagForPublicRequest() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "filter-scenes-user-" + uniqueSuffix + "@example.com";
		String ambientTagName = "ambient-" + uniqueSuffix;
		String showcaseTagName = "showcase-" + uniqueSuffix;

		User savedUser = this.userRepository.saveAndFlush(new User(
				email,
				this.passwordHashingService.hash("unused-password-" + uniqueSuffix),
				"Filter Scenes User"));
		Scene ambientScene = this.sceneRepository.saveAndFlush(new Scene(
				savedUser.getId(),
				"Aurora Drift",
				"Filtered ambient drift.",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						""")));
		Scene otherScene = this.sceneRepository.saveAndFlush(new Scene(
				savedUser.getId(),
				"Signal Bloom",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"pulse"}}
						""")));
		Tag ambientTag = this.tagRepository.saveAndFlush(new Tag(ambientTagName));
		Tag showcaseTag = this.tagRepository.saveAndFlush(new Tag(showcaseTagName));
		this.sceneTagRepository.saveAndFlush(new SceneTag(ambientScene.getId(), ambientTag.getId()));
		this.sceneTagRepository.saveAndFlush(new SceneTag(otherScene.getId(), showcaseTag.getId()));

		this.mockMvc.perform(get("/api/scenes")
				.param("tag", " " + ambientTagName.toUpperCase() + " ")
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].sceneId").value(ambientScene.getId()))
				.andExpect(jsonPath("$[0].creatorDisplayName").value("Filter Scenes User"))
				.andExpect(jsonPath("$[0].name").value("Aurora Drift"))
				.andExpect(jsonPath("$[0].description").value("Filtered ambient drift."))
				.andExpect(jsonPath("$[1]").doesNotExist());
	}

	@Test
	void getScenesReturnsEmptyListWhenNoScenesMatchTagFilter() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "empty-filter-scenes-user-" + uniqueSuffix + "@example.com";

		this.userRepository.saveAndFlush(new User(
				email,
				this.passwordHashingService.hash("unused-password-" + uniqueSuffix),
				"Empty Filter Scenes User"));

		this.mockMvc.perform(get("/api/scenes")
				.param("tag", "ambient")
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0]").doesNotExist());
	}

	@Test
	void getSceneReturnsSceneWithAllFieldsForPublicRequest() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "public-get-scene-user-" + uniqueSuffix + "@example.com";
		String showcaseTagName = "showcase-" + uniqueSuffix;
		String ambientTagName = "ambient-" + uniqueSuffix;

		User savedUser = this.userRepository.saveAndFlush(new User(
				email,
				this.passwordHashingService.hash("unused-password-" + uniqueSuffix),
				"Public Get Scene User"));

		Scene savedScene = this.sceneRepository.saveAndFlush(new Scene(
				savedUser.getId(),
				"Aurora Drift",
				"A public scene detail description.",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"},"state":{"energy":0.92}}
						"""),
				"thumbnails/scene-1.png"));
		Tag showcaseTag = this.tagRepository.saveAndFlush(new Tag(showcaseTagName));
		Tag ambientTag = this.tagRepository.saveAndFlush(new Tag(ambientTagName));
		this.sceneTagRepository.saveAndFlush(new SceneTag(savedScene.getId(), showcaseTag.getId()));
		this.sceneTagRepository.saveAndFlush(new SceneTag(savedScene.getId(), ambientTag.getId()));
		User upvoter = this.userRepository.saveAndFlush(new User(
				"public-upvoter-" + uniqueSuffix + "@example.com",
				this.passwordHashingService.hash("unused-upvoter-password-" + uniqueSuffix),
				"Public Upvoter"));
		User downvoter = this.userRepository.saveAndFlush(new User(
				"public-downvoter-" + uniqueSuffix + "@example.com",
				this.passwordHashingService.hash("unused-downvoter-password-" + uniqueSuffix),
				"Public Downvoter"));
		insertSceneView(savedScene.getId(), null);
		insertSceneView(savedScene.getId(), upvoter.getId());
		insertSceneVote(savedScene.getId(), upvoter.getId(), 1);
		insertSceneVote(savedScene.getId(), downvoter.getId(), -1);
		insertSceneSave(savedScene.getId(), upvoter.getId());

		this.mockMvc.perform(get("/api/scenes/" + savedScene.getId())
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sceneId").value(savedScene.getId()))
				.andExpect(jsonPath("$.ownerUserId").value(savedUser.getId()))
				.andExpect(jsonPath("$.creatorDisplayName").value("Public Get Scene User"))
				.andExpect(jsonPath("$.name").value("Aurora Drift"))
				.andExpect(jsonPath("$.description").value("A public scene detail description."))
				.andExpect(jsonPath("$.sceneData.visualizer.shader").value("nebula"))
				.andExpect(jsonPath("$.sceneData.state.energy").value(0.92))
				.andExpect(jsonPath("$.thumbnailRef").value("thumbnails/scene-1.png"))
				.andExpect(jsonPath("$.createdAt").isNotEmpty())
				.andExpect(jsonPath("$.tags[0]").value(ambientTagName))
				.andExpect(jsonPath("$.tags[1]").value(showcaseTagName))
				.andExpect(jsonPath("$.engagement.views").value(2L))
				.andExpect(jsonPath("$.engagement.upvotes").value(1L))
				.andExpect(jsonPath("$.engagement.downvotes").value(1L))
				.andExpect(jsonPath("$.engagement.saves").value(1L))
				.andExpect(jsonPath("$.engagement.currentUserVote").doesNotExist())
				.andExpect(jsonPath("$.engagement.currentUserSaved").value(false));
	}

	@Test
	void getSceneReturnsSceneWithAllFieldsForAuthenticatedUser() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "get-scene-user-" + uniqueSuffix + "@example.com";
		String password = "password-" + uniqueSuffix;
		String showcaseTagName = "showcase-auth-" + uniqueSuffix;
		String ambientTagName = "ambient-auth-" + uniqueSuffix;

		User savedUser = this.userRepository.saveAndFlush(new User(
				email,
				this.passwordHashingService.hash(password),
				"Get Scene User"));

		String accessToken = accessToken(this.mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequestBody(email, password)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn());

		Scene savedScene = this.sceneRepository.saveAndFlush(new Scene(
				savedUser.getId(),
				"Aurora Drift",
				"An authenticated scene detail description.",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"},"state":{"energy":0.92}}
						"""),
				"thumbnails/scene-1.png"));
		Tag showcaseTag = this.tagRepository.saveAndFlush(new Tag(showcaseTagName));
		Tag ambientTag = this.tagRepository.saveAndFlush(new Tag(ambientTagName));
		this.sceneTagRepository.saveAndFlush(new SceneTag(savedScene.getId(), showcaseTag.getId()));
		this.sceneTagRepository.saveAndFlush(new SceneTag(savedScene.getId(), ambientTag.getId()));
		User otherUser = this.userRepository.saveAndFlush(new User(
				"get-scene-other-" + uniqueSuffix + "@example.com",
				this.passwordHashingService.hash("unused-other-password-" + uniqueSuffix),
				"Get Scene Other User"));
		insertSceneView(savedScene.getId(), null);
		insertSceneView(savedScene.getId(), savedUser.getId());
		insertSceneView(savedScene.getId(), otherUser.getId());
		insertSceneVote(savedScene.getId(), savedUser.getId(), -1);
		insertSceneVote(savedScene.getId(), otherUser.getId(), 1);
		insertSceneSave(savedScene.getId(), savedUser.getId());
		insertSceneSave(savedScene.getId(), otherUser.getId());

		this.mockMvc.perform(get("/api/scenes/" + savedScene.getId())
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sceneId").value(savedScene.getId()))
				.andExpect(jsonPath("$.ownerUserId").value(savedUser.getId()))
				.andExpect(jsonPath("$.creatorDisplayName").value("Get Scene User"))
				.andExpect(jsonPath("$.name").value("Aurora Drift"))
				.andExpect(jsonPath("$.description").value("An authenticated scene detail description."))
				.andExpect(jsonPath("$.sceneData.visualizer.shader").value("nebula"))
				.andExpect(jsonPath("$.sceneData.state.energy").value(0.92))
				.andExpect(jsonPath("$.thumbnailRef").value("thumbnails/scene-1.png"))
				.andExpect(jsonPath("$.createdAt").isNotEmpty())
				.andExpect(jsonPath("$.tags[0]").value(ambientTagName))
				.andExpect(jsonPath("$.tags[1]").value(showcaseTagName))
				.andExpect(jsonPath("$.engagement.views").value(3L))
				.andExpect(jsonPath("$.engagement.upvotes").value(1L))
				.andExpect(jsonPath("$.engagement.downvotes").value(1L))
				.andExpect(jsonPath("$.engagement.saves").value(2L))
				.andExpect(jsonPath("$.engagement.currentUserVote").value("down"))
				.andExpect(jsonPath("$.engagement.currentUserSaved").value(true));
	}

	@Test
	void getSceneReturnsZeroEngagementForPublicRequestWhenSceneHasNoEngagementRows() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "zero-engagement-scene-user-" + uniqueSuffix + "@example.com";

		User savedUser = this.userRepository.saveAndFlush(new User(
				email,
				this.passwordHashingService.hash("unused-password-" + uniqueSuffix),
				"Zero Engagement Scene User"));
		Scene savedScene = this.sceneRepository.saveAndFlush(new Scene(
				savedUser.getId(),
				"Silent Glow",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						""")));

		this.mockMvc.perform(get("/api/scenes/" + savedScene.getId())
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sceneId").value(savedScene.getId()))
				.andExpect(jsonPath("$.engagement.views").value(0L))
				.andExpect(jsonPath("$.engagement.upvotes").value(0L))
				.andExpect(jsonPath("$.engagement.downvotes").value(0L))
				.andExpect(jsonPath("$.engagement.saves").value(0L))
				.andExpect(jsonPath("$.engagement.currentUserVote").doesNotExist())
				.andExpect(jsonPath("$.engagement.currentUserSaved").value(false));
	}

	@Test
	void recordSceneViewPersistsAnonymousViewAndReturnsUpdatedEngagement() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		User savedUser = this.userRepository.saveAndFlush(new User(
				"view-scene-user-" + uniqueSuffix + "@example.com",
				this.passwordHashingService.hash("unused-password-" + uniqueSuffix),
				"View Scene User"));
		Scene savedScene = this.sceneRepository.saveAndFlush(new Scene(
				savedUser.getId(),
				"View Count Glow",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						""")));

		this.mockMvc.perform(post("/api/scenes/" + savedScene.getId() + "/views")
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.views").value(1L))
				.andExpect(jsonPath("$.upvotes").value(0L))
				.andExpect(jsonPath("$.downvotes").value(0L))
				.andExpect(jsonPath("$.saves").value(0L))
				.andExpect(jsonPath("$.currentUserVote").doesNotExist())
				.andExpect(jsonPath("$.currentUserSaved").value(false));

		this.mockMvc.perform(get("/api/scenes/" + savedScene.getId())
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.engagement.views").value(1L));
	}

	@Test
	void authenticatedUserCanVoteAndSaveSceneAndClearBothStates() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "engagement-user-" + uniqueSuffix + "@example.com";
		String password = "password-" + uniqueSuffix;

		User savedUser = this.userRepository.saveAndFlush(new User(
				email,
				this.passwordHashingService.hash(password),
				"Engagement User"));
		Scene savedScene = this.sceneRepository.saveAndFlush(new Scene(
				savedUser.getId(),
				"Engagement Glow",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						""")));

		String accessToken = accessToken(this.mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequestBody(email, password)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn());

		this.mockMvc.perform(put("/api/scenes/" + savedScene.getId() + "/vote")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"vote":"up"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.upvotes").value(1L))
				.andExpect(jsonPath("$.downvotes").value(0L))
				.andExpect(jsonPath("$.currentUserVote").value("up"))
				.andExpect(jsonPath("$.currentUserSaved").value(false));

		this.mockMvc.perform(post("/api/scenes/" + savedScene.getId() + "/save")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.saves").value(1L))
				.andExpect(jsonPath("$.currentUserVote").value("up"))
				.andExpect(jsonPath("$.currentUserSaved").value(true));

		this.mockMvc.perform(delete("/api/scenes/" + savedScene.getId() + "/vote")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.upvotes").value(0L))
				.andExpect(jsonPath("$.currentUserVote").doesNotExist())
				.andExpect(jsonPath("$.currentUserSaved").value(true));

		this.mockMvc.perform(delete("/api/scenes/" + savedScene.getId() + "/save")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.saves").value(0L))
				.andExpect(jsonPath("$.currentUserVote").doesNotExist())
				.andExpect(jsonPath("$.currentUserSaved").value(false));
	}

	@Test
	void getSceneReturnsNotFoundForPublicRequestWhenSceneDoesNotExist() throws Exception {
		this.mockMvc.perform(get("/api/scenes/99999")
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("SCENE_NOT_FOUND"))
				.andExpect(jsonPath("$.message").value("Scene not found."));
	}

	@Test
	void deleteSceneReturnsUnauthorizedWhenRequestHasNoAuthenticationHeader() throws Exception {
		this.mockMvc.perform(delete("/api/scenes/15")
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
				.andExpect(jsonPath("$.message").value("Authentication is required."));
	}

	@Test
	void deleteSceneDeletesOwnedSceneAndRemovesItFromSubsequentReads() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "delete-scene-owner-" + uniqueSuffix + "@example.com";
		String password = "password-" + uniqueSuffix;
		String tagName = "delete-tag-" + uniqueSuffix;

		User savedUser = this.userRepository.saveAndFlush(new User(
				email,
				this.passwordHashingService.hash(password),
				"Delete Scene Owner"));
		Scene savedScene = this.sceneRepository.saveAndFlush(new Scene(
				savedUser.getId(),
				"Aurora Drift",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						""")));
		Tag savedTag = this.tagRepository.saveAndFlush(new Tag(tagName));
		this.sceneTagRepository.saveAndFlush(new SceneTag(savedScene.getId(), savedTag.getId()));

		String accessToken = accessToken(this.mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequestBody(email, password)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn());

		this.mockMvc.perform(delete("/api/scenes/" + savedScene.getId())
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isNoContent());

		assertThat(this.sceneRepository.findById(savedScene.getId())).isEmpty();
		assertThat(this.sceneTagRepository.findAllBySceneId(savedScene.getId())).isEmpty();

		this.mockMvc.perform(get("/api/scenes/" + savedScene.getId())
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("SCENE_NOT_FOUND"))
				.andExpect(jsonPath("$.message").value("Scene not found."));

		this.mockMvc.perform(get("/api/users/" + savedUser.getId() + "/scenes")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0]").doesNotExist());
	}

	@Test
	void deleteSceneReturnsForbiddenWhenAuthenticatedUserDoesNotOwnScene() throws Exception {
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
		Scene savedScene = this.sceneRepository.saveAndFlush(new Scene(
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

		this.mockMvc.perform(delete("/api/scenes/" + savedScene.getId())
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("SCENE_FORBIDDEN"))
				.andExpect(jsonPath("$.message").value("You do not have permission to delete this scene."));

		assertThat(this.sceneRepository.findById(savedScene.getId())).isPresent();
	}

	@Test
	void deleteSceneReturnsNotFoundForNonexistentScene() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "delete-missing-scene-" + uniqueSuffix + "@example.com";
		String password = "password-" + uniqueSuffix;

		this.userRepository.saveAndFlush(new User(
				email,
				this.passwordHashingService.hash(password),
				"Delete Missing Scene User"));

		String accessToken = accessToken(this.mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequestBody(email, password)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn());

		this.mockMvc.perform(delete("/api/scenes/99999")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("SCENE_NOT_FOUND"))
				.andExpect(jsonPath("$.message").value("Scene not found."));
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

	private static Long sceneId(MvcResult result) throws Exception {
		Matcher matcher = Pattern.compile("\"sceneId\":(\\d+)")
				.matcher(result.getResponse().getContentAsString());
		assertThat(matcher.find()).isTrue();
		return Long.valueOf(matcher.group(1));
	}

	private void insertSceneView(Long sceneId, Long userId) {
		this.jdbcTemplate.update(
				"INSERT INTO scene_views (scene_id, user_id) VALUES (?, ?)",
				sceneId,
				userId);
	}

	private void insertSceneVote(Long sceneId, Long userId, int voteValue) {
		this.jdbcTemplate.update(
				"INSERT INTO scene_votes (scene_id, user_id, vote_value) VALUES (?, ?, ?)",
				sceneId,
				userId,
				voteValue);
	}

	private void insertSceneSave(Long sceneId, Long userId) {
		this.jdbcTemplate.update(
				"INSERT INTO scene_saves (scene_id, user_id) VALUES (?, ?)",
				sceneId,
				userId);
	}
}
