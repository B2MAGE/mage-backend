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
@SpringBootTest(properties = { "mage.scene-availability.operator-user-ids=900000002",
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
	void explicitCustomSourceRoundTripsOnlyAfterOperatorEnablesCustomRendering() throws Exception {
		JsonNode document = customDocument("{\"visualizer\":{\"shader\":\"sphere(0.5);\"}}");
		JsonNode created = create(document);
		long id = created.path("sceneId").asLong();
		assertThat(created.path("sceneMode").asText()).isEqualTo("custom-v1");
		assertThat(created.path("availability").path("code").asText()).isEqualTo("CUSTOM_RENDERING_DISABLED");
		assertThat(created.path("sceneData").isNull() || created.path("sceneData").isMissingNode()).isTrue();
		assertThat(this.scenes.findById(id).orElseThrow().getSceneData()).isEqualTo(document);
		this.mvc.perform(put("/api/admin/rendering/custom").header("Authorization", bearer(this.operatorToken))
				.contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true,\"reason\":\"Release tested\"}"))
				.andExpect(status().isOk());
		assertThat(body(this.mvc.perform(get("/api/scenes/{id}", id)).andExpect(status().isOk()).andReturn()).path("sceneData")).isEqualTo(document);
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
		for (JsonNode document : List.of(template(), customDocument("{\"visualizer\":{\"shader\":\"sphere(0.2);\"}}"))) {
			this.mvc.perform(put("/api/scenes/{id}", id).header("Authorization", bearer(this.ownerToken))
					.contentType(MediaType.APPLICATION_JSON).content(request(document).toString()))
					.andExpect(status().isOk()).andExpect(jsonPath("$.availability.code").value("SCENE_DISABLED"))
					.andExpect(jsonPath("$.sceneData").doesNotExist());
		}
		this.mvc.perform(get("/api/admin/scenes/{id}/availability", id).header("Authorization", bearer(this.operatorToken)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.disabled").value(true)).andExpect(jsonPath("$.reason").value("Keep disabled"));
	}

	private JsonNode create(JsonNode document) throws Exception {
		return body(this.mvc.perform(post("/api/scenes").header("Authorization", bearer(this.ownerToken))
				.contentType(MediaType.APPLICATION_JSON).content(request(document).toString())).andExpect(status().isCreated()).andReturn());
	}
	private ObjectNode request(JsonNode document) { return this.json.createObjectNode().put("name", "Contract scene").set("sceneData", document); }
	private JsonNode template() { return this.json.valueToTree(Map.of("schemaVersion", 1, "kind", "template", "templateId", "embedded-scene-0", "templateVersion", 1)); }
	private JsonNode body(MvcResult result) throws Exception { return this.json.readTree(result.getResponse().getContentAsString()); }
	private String bearer(String token) { return "Bearer " + token; }
}
