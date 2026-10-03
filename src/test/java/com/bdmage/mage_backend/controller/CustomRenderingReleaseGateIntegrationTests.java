package com.bdmage.mage_backend.controller;

import com.bdmage.mage_backend.model.Scene;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.repository.SceneRepository;
import com.bdmage.mage_backend.repository.UserRepository;
import com.bdmage.mage_backend.service.AuthenticationTokenService;
import com.bdmage.mage_backend.support.PostgresIntegrationTestSupport;
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
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(properties = {
		"mage.scene-availability.operator-user-ids=900000002",
		"mage.scene-availability.custom-rendering-release-approved=false"
})
@AutoConfigureMockMvc
@Testcontainers
class CustomRenderingReleaseGateIntegrationTests extends PostgresIntegrationTestSupport {

	private static final long OPERATOR_ID = 900000002L;
	@Autowired private MockMvc mvc;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private UserRepository users;
	@Autowired private SceneRepository scenes;
	@Autowired private AuthenticationTokenService tokens;
	private String operatorToken;
	private Scene scene;

	@BeforeEach
	void setUp() throws Exception {
		this.jdbc.update("""
				INSERT INTO users (id, email, password_hash, display_name, first_name, last_name, handle)
				VALUES (?, 'r02-gate-operator@example.com', 'unused-test-hash', 'Operator', 'Operator', '', 'r02gateoperator')
				ON CONFLICT (id) DO NOTHING
				""", OPERATOR_ID);
		User operator = this.users.findById(OPERATOR_ID).orElseThrow();
		this.operatorToken = this.tokens.issueToken(operator);
		this.scene = this.scenes.saveAndFlush(new Scene(operator.getId(), "Unapproved renderer",
				new ObjectMapper().readTree("{\"visualizer\":{\"shader\":\"sphere(0.5);\"}}")));
		this.scene.updateValidatedDocument(new com.bdmage.mage_backend.validation.SceneDocumentValidator().validateAndNormalize(
				com.bdmage.mage_backend.support.SceneDocumentFixtures.customDocument(this.scene.getSceneData())));
		this.scene = this.scenes.saveAndFlush(this.scene);
		reset();
	}

	@AfterEach
	void reset() {
		this.jdbc.update("""
				INSERT INTO custom_rendering_control (id, enabled) VALUES (1, FALSE)
				ON CONFLICT (id) DO UPDATE SET enabled = FALSE, changed_by_user_id = NULL, changed_at = NULL, reason = NULL
				""");
	}

	@Test
	void evenAnOperatorCannotEnableCustomRenderingBeforeTheReleaseGateIsApproved() throws Exception {
		this.mvc.perform(put("/api/admin/rendering/custom").header("Authorization", "Bearer " + this.operatorToken)
				.contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true,\"reason\":\"Try enabling early\"}"))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CUSTOM_RENDERING_RELEASE_REQUIRED"));
		assertThat(this.jdbc.queryForObject("SELECT enabled FROM custom_rendering_control WHERE id = 1", Boolean.class)).isFalse();
		assertThat(this.jdbc.queryForObject("SELECT changed_at IS NULL FROM custom_rendering_control WHERE id = 1", Boolean.class)).isTrue();
		this.mvc.perform(get("/api/admin/rendering/custom").header("Authorization", "Bearer " + this.operatorToken))
				.andExpect(status().isOk()).andExpect(jsonPath("$.releaseApproved").value(false))
				.andExpect(jsonPath("$.enabled").value(false));
	}

	@Test
	void withdrawingReleaseApprovalMakesAPreviouslyEnabledDatabaseFlagIneffective() throws Exception {
		this.jdbc.update("""
				UPDATE custom_rendering_control SET enabled = TRUE, changed_by_user_id = ?,
				changed_at = CURRENT_TIMESTAMP, reason = 'Previously approved release' WHERE id = 1
				""", OPERATOR_ID);
		this.mvc.perform(get("/api/rendering-status"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
		this.mvc.perform(get("/api/scene-availability/{id}", this.scene.getId()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false))
				.andExpect(jsonPath("$.code").value("CUSTOM_RENDERING_DISABLED"));
		this.mvc.perform(get("/api/scenes/{id}", this.scene.getId()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.sceneData").doesNotExist())
				.andExpect(jsonPath("$.availability.available").value(false));
		this.mvc.perform(put("/api/admin/rendering/custom").header("Authorization", "Bearer " + this.operatorToken)
				.contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false,\"reason\":\"Emergency stop remains allowed\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
	}
}
