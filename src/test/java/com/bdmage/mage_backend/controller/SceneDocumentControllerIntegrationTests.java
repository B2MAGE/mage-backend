package com.bdmage.mage_backend.controller;

import java.util.List;
import java.util.Map;

import com.bdmage.mage_backend.model.Scene;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.repository.SceneRepository;
import com.bdmage.mage_backend.repository.UserRepository;
import com.bdmage.mage_backend.service.AuthenticationTokenService;
import com.bdmage.mage_backend.support.PostgresIntegrationTestSupport;
import com.bdmage.mage_backend.service.ThumbnailStorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.bdmage.mage_backend.support.SceneDocumentFixtures.customDocument;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(properties = { "mage.administration.user-ids=900000002",
		"mage.scene-availability.custom-rendering-release-approved=true" })
@AutoConfigureMockMvc
@Testcontainers
class SceneDocumentControllerIntegrationTests extends PostgresIntegrationTestSupport {
	private final ObjectMapper json = new ObjectMapper();
	@Autowired private MockMvc mvc;
	@Autowired private SceneRepository scenes;
	@Autowired private UserRepository users;
	@Autowired private AuthenticationTokenService tokens;
	@Autowired private JdbcTemplate jdbc;
	@MockitoBean private ThumbnailStorageService thumbnails;
	private User owner;
	private String ownerToken;
	private String strangerToken;
	private String operatorToken;

	@BeforeEach
	void setup() {
		this.jdbc.update("""
				INSERT INTO users (id,email,password_hash,display_name,first_name,last_name,handle)
				VALUES (900000002,'b02-operator@example.com','unused','Operator','Operator','','b02operator')
				ON CONFLICT (id) DO NOTHING
				""");
		this.operatorToken = this.tokens.issueToken(this.users.findById(900000002L).orElseThrow());
		this.owner = this.users.saveAndFlush(new User("b02-owner-" + System.nanoTime() + "@example.com", "unused", "Owner"));
		this.ownerToken = this.tokens.issueToken(this.owner);
		this.strangerToken = this.tokens.issueToken(this.users.saveAndFlush(new User("b02-stranger-" + System.nanoTime() + "@example.com", "unused", "Stranger")));
		resetGlobal();
	}

	@AfterEach
	void resetGlobal() {
		this.jdbc.update("UPDATE custom_rendering_control SET enabled=FALSE, changed_by_user_id=NULL, changed_at=NULL, reason=NULL WHERE id=1");
	}

	@Test
	void templatesRoundTripWithoutSourceAndIgnoreOnlyTheCustomGlobalSwitch() throws Exception {
		JsonNode created = create(template());
		long id = created.path("sceneId").asLong();
		assertThat(created.path("sceneMode").asText()).isEqualTo("template-v1");
		assertThat(created.path("availability").path("available").asBoolean()).isTrue();
		JsonNode canonical = created.path("sceneData");
		assertThat(canonical.path("kind").asText()).isEqualTo("template");
		assertThat(canonical.path("parameters").path("scale").isNumber()).isTrue();
		assertThat(canonical.toString()).doesNotContain("shader", "source", "function");
		assertThat(this.scenes.findById(id).orElseThrow().getSceneData()).isEqualTo(canonical);
		assertThat(body(this.mvc.perform(get("/api/scenes/{id}", id)).andExpect(status().isOk()).andReturn()).path("sceneData")).isEqualTo(canonical);
		this.mvc.perform(put("/api/admin/scenes/{id}/availability", id).header("Authorization", bearer(this.operatorToken))
				.contentType(MediaType.APPLICATION_JSON).content("{\"disabled\":true,\"reason\":\"Investigation\"}"))
				.andExpect(status().isOk());
		this.mvc.perform(get("/api/scenes/{id}", id)).andExpect(status().isOk())
				.andExpect(jsonPath("$.sceneMode").value("template-v1"))
				.andExpect(jsonPath("$.sceneData").doesNotExist())
				.andExpect(jsonPath("$.availability.code").value("SCENE_DISABLED"));
	}

	@Test
	void fullTemplateSettingsSurviveCreateUpdateAndReopenWithoutBecomingCustom() throws Exception {
		ObjectNode document = (ObjectNode) template();
		document.set("settings", this.json.readTree("""
				{"camera":{"tilt":0.4,"orientationMode":1},
				 "motion":{"power_factor":4,"easing_speed":0.5},
				 "effects":{"passes":{"rgbShift":true,"afterImage":true},
				   "params":{"rgbShift":{"amount":0.02,"angle":0.5}},"passOrder":["RGBShift","afterImagePass","outputPass"]},
				 "audioResponse":"mapped-v1","audioResponseConfig":{"version":1,"sensitivity":0.6,
				   "mappings":[{"target":"size","source":"bass-hit","amount":0.5,"attack":0.03,"release":0.4}]}}
				"""));
		JsonNode created = create(document);
		long id = created.path("sceneId").asLong();
		assertThat(created.path("sceneMode").asText()).isEqualTo("template-v1");
		assertThat(created.path("availability").path("available").asBoolean()).isTrue();
		ObjectNode updatedDocument = (ObjectNode) created.path("sceneData").deepCopy();
		((ObjectNode) updatedDocument.path("settings").path("effects").path("params").path("rgbShift")).put("amount", 0.04);
		JsonNode updated = body(this.mvc.perform(put("/api/scenes/{id}", id).header("Authorization", bearer(this.ownerToken))
				.contentType(MediaType.APPLICATION_JSON).content(request(updatedDocument).toString()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.sceneMode").value("template-v1")).andReturn());
		JsonNode canonical = updated.path("sceneData");
		assertThat(canonical).isEqualTo(updatedDocument);
		assertThat(canonical.findValues("shader")).isEmpty();
		assertThat(body(this.mvc.perform(get("/api/scenes/{id}", id)).andExpect(status().isOk()).andReturn()).path("sceneData")).isEqualTo(canonical);
		assertThat(body(this.mvc.perform(get("/api/scenes/{id}/repair", id).header("Authorization", bearer(this.ownerToken)))
				.andExpect(status().isOk()).andReturn()).path("sceneData")).isEqualTo(canonical);
		((ObjectNode) updatedDocument.path("settings").path("effects")).put("shader", "sphere(1)");
		this.mvc.perform(put("/api/scenes/{id}", id).header("Authorization", bearer(this.ownerToken))
				.contentType(MediaType.APPLICATION_JSON).content(request(updatedDocument).toString()))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.details['sceneData.settings.effects.shader']").exists());
		assertThat(this.scenes.findById(id).orElseThrow().getSceneData()).isEqualTo(canonical);
	}

	@Test
	void explicitCustomSourceRequiresOperatorEnabledCustomRendering() throws Exception {
		JsonNode document = customDocument("{\"visualizer\":{\"shader\":\"sphere(0.5);\"}}");
		this.mvc.perform(post("/api/scenes").header("Authorization", bearer(this.ownerToken))
				.contentType(MediaType.APPLICATION_JSON).content(request(document).toString()))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CUSTOM_RENDERING_DISABLED"));
		this.mvc.perform(put("/api/admin/rendering/custom").header("Authorization", bearer(this.operatorToken))
				.contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true,\"reason\":\"Release tested\"}"))
				.andExpect(status().isOk());
		JsonNode created = create(document);
		long id = created.path("sceneId").asLong();
		assertThat(created.path("sceneMode").asText()).isEqualTo("custom-v1");
		assertThat(created.path("availability").path("code").asText()).isEqualTo("AVAILABLE");
		assertThat(created.path("sceneData")).isEqualTo(document);
		assertThat(this.scenes.findById(id).orElseThrow().getSceneData()).isEqualTo(document);
	}

	@Test
	void buildersPersistRoundTripAndAuthorizeOnlyTheTrustedBuilderPlaybackPath() throws Exception {
		ObjectNode document = builder();
		ObjectNode authoredObject = (ObjectNode) document.path("objects").get(0);
		authoredObject.putArray("modifiers").addObject().put("type", "twist").put("axis", "y").put("amount", 1.5);
		authoredObject.putArray("arrangements").addObject().put("type", "linear").put("axis", "x").put("count", 3).put("spacing", 1.5);
		authoredObject.putObject("motion").put("type", "spin").put("axis", "y").put("speed", 0.5);
		JsonNode created = create(document);
		long id = created.path("sceneId").asLong();
		assertThat(created.path("sceneMode").asText()).isEqualTo("builder-v1");
		assertThat(created.path("availability").path("code").asText()).isEqualTo("AVAILABLE");
		JsonNode saved = this.scenes.findById(id).orElseThrow().getSceneData().deepCopy();
		assertThat(saved.path("objects").get(0).path("id").asText()).isEqualTo("persistent-shape");
		assertThat(saved.path("objects").get(0).path("operation").path("radius").asDouble()).isEqualTo(1);
		assertThat(saved.path("objects").get(0).path("transform").path("scale").path("x").asDouble()).isEqualTo(1);
		assertThat(saved.path("objects").get(0).path("arrangements").get(0).path("count").asInt()).isEqualTo(3);
		assertThat(saved.path("objects").get(0).path("modifiers").get(0).path("type").asText()).isEqualTo("twist");
		assertThat(saved.path("objects").get(0).path("motion").path("speed").asDouble()).isEqualTo(0.5);
		assertThat(saved.findValues("shader")).isEmpty();
		this.mvc.perform(get("/api/scenes/{id}", id)).andExpect(status().isOk())
				.andExpect(jsonPath("$.sceneMode").value("builder-v1"))
				.andExpect(jsonPath("$.sceneData.kind").value("builder"))
				.andExpect(jsonPath("$.availability.available").value(true));
		assertThat(body(this.mvc.perform(get("/api/scenes/{id}/repair", id).header("Authorization", bearer(this.ownerToken)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.playable").value(false)).andReturn()).path("sceneData")).isEqualTo(saved);
		this.mvc.perform(get("/api/scenes/{id}/repair", id)).andExpect(status().isUnauthorized());
		this.mvc.perform(get("/api/scenes/{id}/repair", id).header("Authorization", bearer(this.strangerToken)))
				.andExpect(status().isForbidden());

		ObjectNode edited = (ObjectNode) saved.deepCopy();
		((ObjectNode) edited.path("objects").get(0).path("operation")).put("radius", 2);
		((ObjectNode) edited.path("objects").get(0)).withArray("bindings").addObject()
				.put("target", "scale.x").put("source", "bass-hit").put("amount", 0.7).put("attack", 0.13).put("release", 0.73);
		this.mvc.perform(put("/api/scenes/{id}", id).header("Authorization", bearer(this.strangerToken))
				.contentType(MediaType.APPLICATION_JSON).content(request(edited).toString())).andExpect(status().isForbidden());
		assertThat(this.scenes.findById(id).orElseThrow().getSceneData()).isEqualTo(saved);
		this.mvc.perform(put("/api/scenes/{id}", id).header("Authorization", bearer(this.ownerToken))
				.contentType(MediaType.APPLICATION_JSON).content(request(edited).toString()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.sceneMode").value("builder-v1"));
		JsonNode after = this.scenes.findById(id).orElseThrow().getSceneData();
		assertThat(after.path("objects").get(0).path("id").asText()).isEqualTo("persistent-shape");
		assertThat(after.path("objects").get(0).path("operation").path("radius").asDouble()).isEqualTo(2);
		assertThat(after.path("objects").get(0).path("bindings").get(0).path("release").asDouble()).isEqualTo(0.73);
		assertThat(body(this.mvc.perform(get("/api/scenes/{id}/repair", id).header("Authorization", bearer(this.ownerToken)))
				.andExpect(status().isOk()).andReturn()).path("sceneData")).isEqualTo(after);
		this.mvc.perform(put("/api/admin/rendering/custom").header("Authorization", bearer(this.operatorToken))
				.contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true,\"reason\":\"Independent custom release\"}"))
				.andExpect(status().isOk());
		this.mvc.perform(get("/api/scene-availability/{id}", id)).andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("AVAILABLE"));
		this.mvc.perform(get("/api/scenes/{id}", id)).andExpect(status().isOk()).andExpect(jsonPath("$.sceneData.kind").value("builder"));
	}

	@Test
	void builderUpdatesPreserveOrderedStagesAndRejectExpandedOverloadsBeforePersistence() throws Exception {
		ObjectNode document = builder();
		ObjectNode object = (ObjectNode) document.path("objects").get(0);
		object.putArray("modifiers")
				.addObject().put("type", "expand").put("amount", 0.2);
		object.withArray("modifiers").addObject().put("type", "shell").put("thickness", 0.1);
		object.withArray("modifiers").addObject().put("type", "twist").put("axis", "y").put("amount", -1.5);
		object.putArray("arrangements").addObject().put("type", "linear").put("axis", "x").put("count", 2).put("spacing", 1);
		object.withArray("arrangements").addObject().put("type", "radial").put("axis", "z").put("count", 8).put("radius", 3);
		object.putObject("motion").put("type", "spin").put("axis", "z").put("speed", -4);
		JsonNode created = create(document);
		long id = created.path("sceneId").asLong();
		ObjectNode edited = (ObjectNode) created.path("sceneData").deepCopy();
		ObjectNode editedObject = (ObjectNode) edited.path("objects").get(0);
		editedObject.putArray("modifiers").addObject().put("type", "shell").put("thickness", 0.2);
		editedObject.withArray("modifiers").addObject().put("type", "expand").put("amount", -0.1);
		((ObjectNode) editedObject.path("motion")).put("speed", 2);
		JsonNode updated = body(this.mvc.perform(put("/api/scenes/{id}", id).header("Authorization", bearer(this.ownerToken))
				.contentType(MediaType.APPLICATION_JSON).content(request(edited).toString()))
				.andExpect(status().isOk()).andReturn()).path("sceneData");
		assertThat(updated).isEqualTo(edited);
		assertThat(body(this.mvc.perform(get("/api/scenes/{id}", id)).andExpect(status().isOk()).andReturn()).path("sceneData"))
				.isEqualTo(edited);

		ObjectNode overloaded = edited.deepCopy();
		((ObjectNode) overloaded.path("objects").get(0).path("arrangements").get(0)).put("count", 3);
		long sceneCount = this.scenes.count();
		this.mvc.perform(post("/api/scenes").header("Authorization", bearer(this.ownerToken))
				.contentType(MediaType.APPLICATION_JSON).content(request(overloaded).toString()))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.details['sceneData.objects']").exists());
		this.mvc.perform(put("/api/scenes/{id}", id).header("Authorization", bearer(this.ownerToken))
				.contentType(MediaType.APPLICATION_JSON).content(request(overloaded).toString()))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.details['sceneData.objects']").exists());
		assertThat(this.scenes.count()).isEqualTo(sceneCount);
		assertThat(this.scenes.findById(id).orElseThrow().getSceneData()).isEqualTo(edited);
		verifyNoInteractions(this.thumbnails);
	}

	@Test
	void currentSharedDocumentsSurviveApiCreateUpdateAndReadWithoutContractDrift() throws Exception {
		JsonNode fixtures;
		try (var input = getClass().getResourceAsStream("/contracts/scenes/current-round-trips.json")) {
			assertThat(input).isNotNull();
			fixtures = this.json.readTree(input);
		}
		this.mvc.perform(put("/api/admin/rendering/custom").header("Authorization", bearer(this.operatorToken))
				.contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true,\"reason\":\"Current contract verification\"}"))
				.andExpect(status().isOk());
		for (JsonNode row : fixtures.path("cases")) {
			if (!row.path("valid").asBoolean()) continue;
			JsonNode created = create(row.path("input"));
			long id = created.path("sceneId").asLong();
			JsonNode expected = row.path("normalized");
			assertThat(created.path("sceneData")).as(row.path("name").asText()).isEqualTo(expected);
			JsonNode updated = body(this.mvc.perform(put("/api/scenes/{id}", id).header("Authorization", bearer(this.ownerToken))
					.contentType(MediaType.APPLICATION_JSON).content(request(expected).toString()))
					.andExpect(status().isOk()).andReturn());
			assertThat(updated.path("sceneData")).isEqualTo(expected);
			assertThat(this.scenes.findById(id).orElseThrow().getSceneData()).isEqualTo(expected);
			assertThat(body(this.mvc.perform(get("/api/scenes/{id}", id)).andExpect(status().isOk()).andReturn()).path("sceneData"))
					.isEqualTo(expected);
		}
	}

	@Test
	void blockedBuilderCanBeEditedAndExportedWithoutClearingModeration() throws Exception {
		long id = create(builder()).path("sceneId").asLong();
		this.mvc.perform(put("/api/admin/scenes/{id}/availability", id).header("Authorization", bearer(this.operatorToken))
				.contentType(MediaType.APPLICATION_JSON).content("{\"disabled\":true,\"reason\":\"Keep this block\"}"))
				.andExpect(status().isOk());
		ObjectNode edited = builder();
		edited.putObject("parameters").put("speed", 0.3);
		this.mvc.perform(put("/api/scenes/{id}", id).header("Authorization", bearer(this.ownerToken))
				.contentType(MediaType.APPLICATION_JSON).content(request(edited).put("description", "Updated while blocked").toString()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.availability.code").value("SCENE_DISABLED"))
				.andExpect(jsonPath("$.sceneData").doesNotExist());
		this.mvc.perform(get("/api/admin/scenes/{id}/availability", id).header("Authorization", bearer(this.operatorToken)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.disabled").value(true))
				.andExpect(jsonPath("$.reason").value("Keep this block"));
		this.mvc.perform(get("/api/scenes/{id}/repair", id).header("Authorization", bearer(this.ownerToken)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.playable").value(false))
				.andExpect(jsonPath("$.sceneData.kind").value("builder"))
				.andExpect(jsonPath("$.sceneData.parameters.speed").value(0.3))
				.andExpect(jsonPath("$.description").value("Updated while blocked"));
	}

	@Test
	void rejectsInvalidBuilderCreatesAndUpdatesBeforeAnyContentOrThumbnailMutation() throws Exception {
		long id = create(builder()).path("sceneId").asLong();
		JsonNode saved = this.scenes.findById(id).orElseThrow().getSceneData().deepCopy();
		long count = this.scenes.count();
		String[] payloads = {
				"{\"schemaVersion\":1,\"kind\":\"builder\",\"builderVersion\":2,\"objects\":[]}",
				"{\"schemaVersion\":1,\"kind\":\"builder\",\"builderVersion\":1,\"objects\":[],\"scene\":{\"visualizer\":{\"shader\":\"NEVER_EXECUTE\"}}}",
				"{\"schemaVersion\":1,\"kind\":\"builder\",\"builderVersion\":1,\"objects\":[{\"id\":\"shape\",\"operation\":{\"type\":\"sphere\",\"radius\":0}}]}",
				"{\"schemaVersion\":1,\"kind\":\"builder\",\"builderVersion\":1,\"objects\":[{\"id\":\"shape\",\"operation\":{\"type\":\"sphere\"},\"bindings\":[{\"target\":\"scale.x\",\"source\":\"fetch('NEVER_EXECUTE')\"}]}]}",
		};
		String[] paths = { "sceneData.builderVersion", "sceneData.scene", "sceneData.objects[0].operation.radius", "sceneData.objects[0].bindings[0].source" };
		for (int index = 0; index < payloads.length; index++) {
			ObjectNode rejected = request(this.json.readTree(payloads[index])).put("name", "Must not persist")
					.put("thumbnailObjectKey", "pending/unfinalized.png");
			this.mvc.perform(post("/api/scenes").header("Authorization", bearer(this.ownerToken))
					.contentType(MediaType.APPLICATION_JSON).content(rejected.toString()))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
					.andExpect(jsonPath("$.details['" + paths[index] + "']").exists());
			rejected.remove("thumbnailObjectKey");
			this.mvc.perform(put("/api/scenes/{id}", id).header("Authorization", bearer(this.ownerToken))
					.contentType(MediaType.APPLICATION_JSON).content(rejected.toString()))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.details['" + paths[index] + "']").exists());
		}
		assertThat(this.scenes.count()).isEqualTo(count);
		Scene unchanged = this.scenes.findById(id).orElseThrow();
		assertThat(unchanged.getName()).isEqualTo("Contract scene");
		assertThat(unchanged.getSceneData()).isEqualTo(saved);
		assertThat(unchanged.getSceneMode()).isEqualTo(Scene.BUILDER_V1);
		verifyNoInteractions(this.thumbnails);
	}

	@Test
	void invalidDocumentsAndForgedRequestFieldsNeverPartiallyPersistOrFinalizeThumbnails() throws Exception {
		JsonNode original = template();
		long id = create(original).path("sceneId").asLong();
		JsonNode saved = this.scenes.findById(id).orElseThrow().getSceneData().deepCopy();
		long count = this.scenes.count();
		List<String> invalidPaths = List.of("sceneData.schemaVersion", "sceneData.schemaVersion", "sceneData.kind",
				"sceneData.templateVersion", "sceneData.templateId", "sceneData.parameters.shader", "sceneData.scene.intent.fov");
		int invalidIndex = 0;
		for (String invalid : List.of(
				"{\"visualizer\":{\"shader\":\"legacy write\"}}",
				"{\"schemaVersion\":2,\"kind\":\"template\",\"templateId\":\"embedded-scene-0\",\"templateVersion\":1}",
				"{\"schemaVersion\":1,\"kind\":\"legacy-custom\",\"scene\":{}}",
				"{\"schemaVersion\":1,\"kind\":\"template\",\"templateId\":\"embedded-scene-0\",\"templateVersion\":2}",
				"{\"schemaVersion\":1,\"kind\":\"template\",\"templateId\":\"unknown\",\"templateVersion\":1}",
				"{\"schemaVersion\":1,\"kind\":\"template\",\"templateId\":\"embedded-scene-0\",\"templateVersion\":1,\"parameters\":{\"shader\":\"secret-source\"}}",
				"{\"schemaVersion\":1,\"kind\":\"custom\",\"scene\":{\"visualizer\":{\"shader\":\"source\"},\"intent\":{\"fov\":180}}}")) {
			ObjectNode request = request(this.json.readTree(invalid));
			request.put("thumbnailObjectKey", "scenes/pending/" + this.owner.getId() + "/thumbnails/not-finalized.png");
			JsonNode rejected = body(this.mvc.perform(post("/api/scenes").header("Authorization", bearer(this.ownerToken))
					.contentType(MediaType.APPLICATION_JSON).content(request.toString()))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR")).andReturn());
			assertThat(rejected.path("details").has(invalidPaths.get(invalidIndex++))).isTrue();
			request.remove("thumbnailObjectKey");
			this.mvc.perform(put("/api/scenes/{id}", id).header("Authorization", bearer(this.ownerToken))
					.contentType(MediaType.APPLICATION_JSON).content(request.toString()))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		}
		for (String field : List.of("sceneMode", "disabled", "availability", "ownerUserId")) {
			ObjectNode request = request(template()).put(field, "forged");
			this.mvc.perform(post("/api/scenes").header("Authorization", bearer(this.ownerToken))
					.contentType(MediaType.APPLICATION_JSON).content(request.toString())).andExpect(status().isBadRequest());
			this.mvc.perform(put("/api/scenes/{id}", id).header("Authorization", bearer(this.ownerToken))
					.contentType(MediaType.APPLICATION_JSON).content(request.toString())).andExpect(status().isBadRequest());
		}
		assertThat(this.scenes.count()).isEqualTo(count);
		assertThat(this.scenes.findById(id).orElseThrow().getSceneData()).isEqualTo(saved);
		verifyNoInteractions(this.thumbnails);
	}

	@Test
	void legacyReadsKeepStoredJsonExactAndOnlyOwnerCanExplicitlyUpgrade() throws Exception {
		JsonNode legacy = this.json.readTree("{\"visualizer\":{\"shader\":\"old source\"},\"privateLegacyKey\":\"preserve me\"}");
		Scene saved = this.scenes.saveAndFlush(new Scene(this.owner.getId(), "Legacy", legacy));
		long id = saved.getId();
		this.mvc.perform(get("/api/scenes/{id}", id)).andExpect(status().isOk())
				.andExpect(jsonPath("$.sceneMode").value("legacy-custom"))
				.andExpect(jsonPath("$.availability.code").value("SCENE_UPGRADE_REQUIRED"))
				.andExpect(jsonPath("$.sceneData").doesNotExist());
		assertThat(body(this.mvc.perform(get("/api/scenes/{id}/repair", id).header("Authorization", bearer(this.ownerToken)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.playable").value(false)).andReturn()).path("sceneData")).isEqualTo(legacy);
		assertThat(this.scenes.findById(id).orElseThrow().getSceneData()).isEqualTo(legacy);
		this.mvc.perform(get("/api/scenes/{id}/repair", id)).andExpect(status().isUnauthorized());
		this.mvc.perform(get("/api/scenes/{id}/repair", id).header("Authorization", bearer(this.strangerToken))).andExpect(status().isForbidden());
		this.mvc.perform(put("/api/scenes/{id}", id).header("Authorization", bearer(this.strangerToken))
				.contentType(MediaType.APPLICATION_JSON).content(request(template()).toString())).andExpect(status().isForbidden());
		this.mvc.perform(put("/api/scenes/{id}", id).header("Authorization", bearer(this.ownerToken))
				.contentType(MediaType.APPLICATION_JSON).content(request(template()).toString()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.sceneMode").value("template-v1"))
				.andExpect(jsonPath("$.availability.available").value(true));
	}

	@Test
	void upgradingLegacyOrSwitchingDocumentKindsNeverReenablesAnOperatorDisabledScene() throws Exception {
		Scene legacy = this.scenes.saveAndFlush(new Scene(this.owner.getId(), "Legacy", this.json.readTree("{\"visualizer\":{\"shader\":\"old\"}}")));
		long id = legacy.getId();
		this.mvc.perform(put("/api/admin/scenes/{id}/availability", id).header("Authorization", bearer(this.operatorToken))
				.contentType(MediaType.APPLICATION_JSON).content("{\"disabled\":true,\"reason\":\"Keep disabled\"}"))
				.andExpect(status().isOk());
		this.mvc.perform(put("/api/scenes/{id}", id).header("Authorization", bearer(this.ownerToken))
				.contentType(MediaType.APPLICATION_JSON).content(request(template()).toString()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.availability.code").value("SCENE_DISABLED"))
				.andExpect(jsonPath("$.sceneData").doesNotExist());
		this.mvc.perform(put("/api/scenes/{id}", id).header("Authorization", bearer(this.ownerToken))
				.contentType(MediaType.APPLICATION_JSON)
				.content(request(customDocument("{\"visualizer\":{\"shader\":\"sphere(0.2);\"}}")).toString()))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CUSTOM_RENDERING_DISABLED"));
		this.mvc.perform(get("/api/admin/scenes/{id}/availability", id).header("Authorization", bearer(this.operatorToken)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.disabled").value(true)).andExpect(jsonPath("$.reason").value("Keep disabled"));
	}

	private JsonNode create(JsonNode document) throws Exception {
		return body(this.mvc.perform(post("/api/scenes").header("Authorization", bearer(this.ownerToken))
				.contentType(MediaType.APPLICATION_JSON).content(request(document).toString())).andExpect(status().isCreated()).andReturn());
	}
	private ObjectNode request(JsonNode document) { return this.json.createObjectNode().put("name", "Contract scene").<ObjectNode>set("tagIds", this.json.createArrayNode()).set("sceneData", document); }
	private JsonNode template() { return this.json.valueToTree(Map.of("schemaVersion", 1, "kind", "template", "templateId", "embedded-scene-0", "templateVersion", 1)); }
	private ObjectNode builder() {
		ObjectNode document = this.json.createObjectNode().put("schemaVersion", 1).put("kind", "builder").put("builderVersion", 1);
		document.putArray("objects").addObject().put("id", "persistent-shape").putObject("operation").put("type", "sphere");
		return document;
	}
	private JsonNode body(MvcResult result) throws Exception { return this.json.readTree(result.getResponse().getContentAsString()); }
	private String bearer(String token) { return "Bearer " + token; }
}
