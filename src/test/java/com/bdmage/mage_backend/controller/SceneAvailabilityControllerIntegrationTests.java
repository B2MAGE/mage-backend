package com.bdmage.mage_backend.controller;

import static com.bdmage.mage_backend.support.SceneDocumentFixtures.explicitCustomSceneDocument;
import static com.bdmage.mage_backend.support.SceneDocumentFixtures.customDocument;
import com.bdmage.mage_backend.validation.SceneDocumentValidator;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import com.bdmage.mage_backend.model.Scene;
import com.bdmage.mage_backend.model.SceneTag;
import com.bdmage.mage_backend.model.Tag;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.repository.SceneRepository;
import com.bdmage.mage_backend.repository.SceneTagRepository;
import com.bdmage.mage_backend.repository.TagRepository;
import com.bdmage.mage_backend.repository.UserRepository;
import com.bdmage.mage_backend.service.AuthenticationTokenService;
import com.bdmage.mage_backend.service.ThumbnailStorageService;
import com.bdmage.mage_backend.support.PostgresIntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(properties = {
		"mage.scene-availability.operator-user-ids=900000001",
		"mage.scene-availability.custom-rendering-release-approved=true"
})
@AutoConfigureMockMvc
@Testcontainers
class SceneAvailabilityControllerIntegrationTests extends PostgresIntegrationTestSupport {

	private static final long OPERATOR_ID = 900000001L;
	private static final String PRIVATE_REASON = "Internal investigation: private operator notes";
	private static final String SHADER = "sphere(0.5); // private executable source";
	private final ObjectMapper json = new ObjectMapper();

	@Autowired private MockMvc mvc;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private UserRepository users;
	@Autowired private SceneRepository scenes;
	@Autowired private TagRepository tags;
	@Autowired private SceneTagRepository sceneTags;
	@Autowired private AuthenticationTokenService tokens;
	@MockitoBean private ThumbnailStorageService thumbnailStorage;

	private String operatorToken;
	private String ownerToken;
	private String strangerToken;
	private User owner;
	private Scene scene;

	@BeforeEach
	void createIdentitiesAndScene() throws Exception {
		this.jdbc.update("""
				INSERT INTO users (id, email, password_hash, display_name, first_name, last_name, handle)
				VALUES (?, 'r02-operator@example.com', 'unused-test-hash', 'Operator', 'Operator', '', 'r02operator')
				ON CONFLICT (id) DO NOTHING
				""", OPERATOR_ID);
		this.operatorToken = this.tokens.issueToken(this.users.findById(OPERATOR_ID).orElseThrow());
		this.owner = user("owner");
		this.ownerToken = this.tokens.issueToken(this.owner);
		this.strangerToken = this.tokens.issueToken(user("stranger"));
		this.scene = this.scenes.saveAndFlush(new Scene(this.owner.getId(), "Availability test",
				this.json.valueToTree(Map.of("visualizer", Map.of("shader", SHADER)))));
		this.scene.updateValidatedDocument(new SceneDocumentValidator().validateAndNormalize(customDocument(this.scene.getSceneData())));
		this.scene = this.scenes.saveAndFlush(this.scene);
		resetGlobalControl();
	}

	@AfterEach
	void resetGlobalControl() {
		this.jdbc.update("""
				INSERT INTO custom_rendering_control (id, enabled) VALUES (1, FALSE)
				ON CONFLICT (id) DO UPDATE SET enabled = FALSE, changed_by_user_id = NULL, changed_at = NULL, reason = NULL
				""");
	}

	@Test
	void adminReadsAndWritesRequireAnAuthenticatedAllowlistedOperator() throws Exception {
		for (String path : List.of(adminScenePath(), "/api/admin/rendering/custom")) {
			String body = path.equals(adminScenePath())
					? "{\"disabled\":true,\"reason\":\"Private\"}"
					: "{\"enabled\":true,\"reason\":\"Private\"}";
			for (MockHttpServletRequestBuilder request : List.of(get(path), put(path)
					.contentType(MediaType.APPLICATION_JSON).content(body))) {
				this.mvc.perform(request)
						.andExpect(status().isUnauthorized())
						.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
						.andExpect(header().string("Cache-Control", containsString("no-store")));
			}
			for (String token : List.of(this.ownerToken, this.strangerToken)) {
				this.mvc.perform(get(path).header("Authorization", bearer(token)))
						.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("OPERATOR_ACCESS_REQUIRED"));
				this.mvc.perform(put(path).header("Authorization", bearer(token))
						.contentType(MediaType.APPLICATION_JSON).content(body))
						.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("OPERATOR_ACCESS_REQUIRED"));
			}
			this.mvc.perform(get(path).header("Authorization", bearer(this.operatorToken)))
					.andExpect(status().isOk());
		}
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM scene_availability_controls WHERE scene_id = ?",
				Long.class, this.scene.getId())).isZero();
	}

	@Test
	void repeatedDisableAndEnablePreserveTheOriginalTransitionAudit() throws Exception {
		enableCustom();
		JsonNode disabled = changeScene(true, PRIVATE_REASON);
		assertThat(disabled.path("disabled").asBoolean()).isTrue();
		assertThat(disabled.path("changedByUserId").asLong()).isEqualTo(OPERATOR_ID);
		assertThat(disabled.path("changedAt").asText()).isNotBlank();
		assertThat(changeScene(true, "A duplicate request must not replace the reason")).isEqualTo(disabled);
		JsonNode audit = body(this.mvc.perform(get(adminScenePath()).header("Authorization", bearer(this.operatorToken)))
				.andExpect(status().isOk()).andReturn());
		assertThat(audit).isEqualTo(disabled);

		JsonNode enabled = changeScene(false, "Repaired and checked");
		assertThat(enabled.path("disabled").asBoolean()).isFalse();
		assertThat(enabled.path("reason").asText()).isEqualTo("Repaired and checked");
		assertThat(changeScene(false, "Another duplicate")).isEqualTo(enabled);
		this.mvc.perform(get("/api/scenes/{id}", this.scene.getId()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.sceneData.scene.visualizer.shader").value(SHADER))
				.andExpect(jsonPath("$.availability.available").value(true));
	}

	@Test
	void disabledContentIsSuppressedAcrossEveryPublicSceneCollectionAndAuthenticatedDetail() throws Exception {
		enableCustom();
		changeScene(true, PRIVATE_REASON);
		Tag tag = this.tags.saveAndFlush(new Tag("availability-" + System.nanoTime()));
		this.sceneTags.saveAndFlush(new SceneTag(this.scene.getId(), tag.getId()));
		for (MockHttpServletRequestBuilder request : List.of(
				get("/api/scenes/{id}", this.scene.getId()),
				get("/api/scenes/{id}", this.scene.getId()).header("Authorization", bearer(this.ownerToken)),
				get("/api/scenes/{id}", this.scene.getId()).header("Authorization", bearer(this.operatorToken)),
				get("/api/scenes"), get("/api/scenes").param("tag", tag.getName()),
				get("/api/users/{id}/scenes", this.owner.getId()).header("Authorization", bearer(this.ownerToken)),
				get("/api/profiles/{handle}", this.owner.getHandle()))) {
			MvcResult result = this.mvc.perform(request.header("If-None-Match", "*")
					.header("If-Modified-Since", "Fri, 01 Jan 2100 00:00:00 GMT"))
					.andExpect(status().isOk()).andExpect(header().string("Cache-Control", containsString("no-store")))
					.andReturn();
			JsonNode disabledScene = findScene(body(result));
			assertSuppressed(disabledScene, "SCENE_DISABLED");
			assertThat(disabledScene.toString()).doesNotContain(SHADER);
			assertThat(result.getResponse().getContentAsString()).doesNotContain(PRIVATE_REASON, "changedByUserId", "changedAt");
		}
		assertThat(this.scenes.findById(this.scene.getId()).orElseThrow().getSceneData().path("scene").path("visualizer").path("shader").asText())
				.isEqualTo(SHADER);
	}

	@Test
	void creatorUpdatesAndImportedReplacementCannotClearTheSeparateDisableState() throws Exception {
		enableCustom();
		JsonNode originalControl = changeScene(true, PRIVATE_REASON);
		String importedBody = this.json.writeValueAsString(Map.of(
				"name", "Imported replacement", "description", "Edited",
				"sceneData", Map.of("visualizer", Map.of("shader", "sphere(0.7);"))));
		for (MockHttpServletRequestBuilder request : List.of(
				put("/api/scenes/{id}", this.scene.getId()).with(explicitCustomSceneDocument()).content(importedBody),
				patch("/api/scenes/{id}/description", this.scene.getId()).content("{\"description\":\"New description\",\"disabled\":false}"))) {
			MvcResult result = this.mvc.perform(request.header("Authorization", bearer(this.ownerToken))
					.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();
			assertSuppressed(body(result), "SCENE_DISABLED");
		}
		assertThat(this.scenes.findById(this.scene.getId()).orElseThrow().getSceneData().path("scene").path("visualizer").path("shader").asText())
				.isEqualTo("sphere(0.7);");
		assertThat(body(this.mvc.perform(get(adminScenePath()).header("Authorization", bearer(this.operatorToken)))
				.andExpect(status().isOk()).andReturn())).isEqualTo(originalControl);
	}

	@Test
	void thumbnailFinalizationCannotLeakDisabledSource() throws Exception {
		enableCustom();
		changeScene(true, PRIVATE_REASON);
		when(this.thumbnailStorage.finalizeUpload(this.scene.getId(), "uploaded-thumbnail"))
				.thenReturn(new ThumbnailStorageService.FinalizedThumbnail("uploaded-thumbnail", "https://cdn.test.example.com/new.png"));
		MvcResult result = this.mvc.perform(post("/api/scenes/{id}/thumbnail/finalize", this.scene.getId()).with(explicitCustomSceneDocument())
				.header("Authorization", bearer(this.ownerToken)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"objectKey\":\"uploaded-thumbnail\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.thumbnailRef").value("https://cdn.test.example.com/new.png"))
				.andReturn();
		assertSuppressed(body(result), "SCENE_DISABLED");
	}

	@Test
	void repairReadIsExplicitlyNonPlayableAndOnlyTheOwnerCanObtainItsSource() throws Exception {
		changeScene(true, PRIVATE_REASON);
		String repair = "/api/scenes/" + this.scene.getId() + "/repair";
		this.mvc.perform(get(repair)).andExpect(status().isUnauthorized());
		for (String token : List.of(this.strangerToken, this.operatorToken)) {
			this.mvc.perform(get(repair).header("Authorization", bearer(token)))
					.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("SCENE_OWNERSHIP_REQUIRED"));
		}
		MvcResult result = this.mvc.perform(get(repair).header("Authorization", bearer(this.ownerToken)))
				.andExpect(status().isOk()).andExpect(header().string("Cache-Control", containsString("no-store")))
				.andExpect(jsonPath("$.sceneData.scene.visualizer.shader").value(SHADER))
				.andExpect(jsonPath("$.playable").value(false))
				.andExpect(jsonPath("$.availability.available").value(false)).andReturn();
		assertThat(result.getResponse().getContentAsString()).doesNotContain(PRIVATE_REASON, "changedByUserId");
	}

	@Test
	void customSwitchDefaultsOffAndMissingStateIsAlsoFailClosed() throws Exception {
		for (int attempt = 0; attempt < 2; attempt++) {
			this.mvc.perform(get("/api/rendering-status"))
					.andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
			assertSuppressed(body(this.mvc.perform(get("/api/scenes/{id}", this.scene.getId()))
					.andExpect(status().isOk()).andReturn()), "CUSTOM_RENDERING_DISABLED");
			this.jdbc.update("DELETE FROM custom_rendering_control");
		}
		enableCustom();
		this.mvc.perform(get("/api/scenes/{id}", this.scene.getId()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.sceneData.scene.visualizer.shader").value(SHADER));
	}

	@Test
	void customSwitchIsIdempotentAuditedAndTakesEffectOnFollowingReads() throws Exception {
		JsonNode enabled = changeCustom(true, PRIVATE_REASON);
		assertThat(enabled.path("changedByUserId").asLong()).isEqualTo(OPERATOR_ID);
		assertThat(enabled.path("changedAt").asText()).isNotBlank();
		assertThat(changeCustom(true, "Duplicate")).isEqualTo(enabled);
		this.mvc.perform(get("/api/scene-availability/{id}", this.scene.getId()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.available").value(true));
		JsonNode disabled = changeCustom(false, "Emergency stop");
		assertThat(changeCustom(false, "Duplicate stop")).isEqualTo(disabled);
		for (String path : List.of("/api/rendering-status", "/api/scene-availability/" + this.scene.getId())) {
			MvcResult result = this.mvc.perform(get(path).header("If-None-Match", "*"))
					.andExpect(status().isOk()).andExpect(header().string("Cache-Control", containsString("no-store"))).andReturn();
			assertThat(result.getResponse().getContentAsString()).doesNotContain(PRIVATE_REASON, "Emergency stop", "reason", "changedByUserId");
		}
		assertSuppressed(body(this.mvc.perform(get("/api/scenes/{id}", this.scene.getId()))
				.andExpect(status().isOk()).andReturn()), "CUSTOM_RENDERING_DISABLED");
	}

	@Test
	void creationWhileCustomRenderingIsOffPersistsSourceWithoutReturningExecutableContent() throws Exception {
		MvcResult result = this.mvc.perform(post("/api/scenes").with(explicitCustomSceneDocument()).header("Authorization", bearer(this.ownerToken))
				.contentType(MediaType.APPLICATION_JSON).content(this.json.writeValueAsString(Map.of(
						"name", "New scene", "sceneData", Map.of("visualizer", Map.of("shader", SHADER))))))
				.andExpect(status().isCreated()).andReturn();
		JsonNode response = body(result);
		assertSuppressed(response, "CUSTOM_RENDERING_DISABLED");
		assertThat(this.scenes.findById(response.path("sceneId").asLong()).orElseThrow().getSceneData()
				.path("scene").path("visualizer").path("shader").asText()).isEqualTo(SHADER);
	}

	@Test
	void batchAvailabilityIsBoundedAndRepresentsMissingScenesWithoutSourceOrPrivateAudit() throws Exception {
		enableCustom();
		changeScene(true, PRIVATE_REASON);
		long missing = Long.MAX_VALUE;
		MvcResult result = this.mvc.perform(get("/api/scene-availability")
				.param("ids", this.scene.getId() + "," + missing))
				.andExpect(status().isOk()).andExpect(header().string("Cache-Control", containsString("no-store")))
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].available").value(false))
				.andExpect(jsonPath("$[1].sceneId").value(missing))
				.andExpect(jsonPath("$[1].available").value(false))
				.andExpect(jsonPath("$[1].code").value("SCENE_NOT_FOUND")).andReturn();
		assertThat(result.getResponse().getContentAsString()).doesNotContain(SHADER, PRIVATE_REASON, "sceneData", "changedByUserId");
		String oversized = LongStream.rangeClosed(1, 101).mapToObj(Long::toString).collect(Collectors.joining(","));
		for (String ids : List.of("", "0", "-1", "abc", oversized)) {
			this.mvc.perform(get("/api/scene-availability").param("ids", ids))
					.andExpect(status().isBadRequest());
		}
		this.mvc.perform(get("/api/scene-availability")).andExpect(status().isBadRequest());
		this.mvc.perform(get("/api/scene-availability/{id}", missing))
				.andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false))
				.andExpect(jsonPath("$.code").value("SCENE_NOT_FOUND"));
	}

	@Test
	void operatorMutationsRejectMissingFlagsAndBlankOrOversizedReasons() throws Exception {
		for (String path : List.of(adminScenePath(), "/api/admin/rendering/custom")) {
			String flag = path.equals(adminScenePath()) ? "disabled" : "enabled";
			for (String body : List.of("{\"reason\":\"No flag\"}", "{\"" + flag + "\":true}",
					this.json.writeValueAsString(Map.of(flag, true, "reason", "  ")),
					this.json.writeValueAsString(Map.of(flag, true, "reason", "x".repeat(1001))))) {
				this.mvc.perform(put(path).header("Authorization", bearer(this.operatorToken))
						.contentType(MediaType.APPLICATION_JSON).content(body))
						.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
			}
		}
	}

	private User user(String label) {
		return this.users.saveAndFlush(new User("r02-" + label + "-" + System.nanoTime() + "@example.com",
				"unused-test-hash", label));
	}

	private String adminScenePath() { return "/api/admin/scenes/" + this.scene.getId() + "/availability"; }
	private static String bearer(String token) { return "Bearer " + token; }
	private JsonNode body(MvcResult result) throws Exception { return this.json.readTree(result.getResponse().getContentAsString()); }
	private void enableCustom() throws Exception { changeCustom(true, "Isolation acceptance approved for this test"); }

	private JsonNode changeScene(boolean disabled, String reason) throws Exception {
		return body(this.mvc.perform(put(adminScenePath()).header("Authorization", bearer(this.operatorToken))
				.contentType(MediaType.APPLICATION_JSON).content(this.json.writeValueAsString(Map.of("disabled", disabled, "reason", reason))))
				.andExpect(status().isOk()).andExpect(header().string("Cache-Control", containsString("no-store"))).andReturn());
	}

	private JsonNode changeCustom(boolean enabled, String reason) throws Exception {
		return body(this.mvc.perform(put("/api/admin/rendering/custom").header("Authorization", bearer(this.operatorToken))
				.contentType(MediaType.APPLICATION_JSON).content(this.json.writeValueAsString(Map.of("enabled", enabled, "reason", reason))))
				.andExpect(status().isOk()).andReturn());
	}

	private JsonNode findScene(JsonNode response) {
		if (response.has("sceneId")) return response;
		JsonNode collection = response.isArray() ? response : response.path("scenes");
		for (JsonNode item : collection) {
			if (item.path("sceneId").asLong() == this.scene.getId()) return item;
		}
		throw new AssertionError("Scene was missing from its collection response");
	}

	private static void assertSuppressed(JsonNode response, String code) {
		assertThat(response.path("sceneData").isNull()).isTrue();
		assertThat(response.path("availability").path("available").asBoolean()).isFalse();
		assertThat(response.path("availability").path("code").asText()).isEqualTo(code);
	}
}
