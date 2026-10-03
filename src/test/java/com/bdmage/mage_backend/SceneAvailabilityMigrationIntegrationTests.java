package com.bdmage.mage_backend;

import javax.sql.DataSource;

import com.bdmage.mage_backend.model.Scene;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.repository.SceneRepository;
import com.bdmage.mage_backend.repository.UserRepository;
import com.bdmage.mage_backend.support.PostgresIntegrationTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
@Testcontainers
class SceneAvailabilityMigrationIntegrationTests extends PostgresIntegrationTestSupport {

	@Autowired private DataSource dataSource;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private SceneRepository scenes;
	@Autowired private UserRepository users;

	@Test
	void upgradingAnExistingDatabaseLeavesStoredScenesIntactAndStartsCustomRenderingDisabled() {
		String schema = "r02_upgrade_" + System.nanoTime();
		try {
			Flyway.configure().dataSource(this.dataSource).schemas(schema).defaultSchema(schema)
					.locations("classpath:db/migration").target("17").load().migrate();
			this.jdbc.update("INSERT INTO " + schema + ".users "
					+ "(id,email,password_hash,display_name,first_name,last_name,handle) "
					+ "VALUES (1,'legacy@example.com','legacy-hash','Legacy','Legacy','','legacy')");
			this.jdbc.update("INSERT INTO " + schema + ".scenes (id,owner_user_id,name,description,scene_data,thumbnail_ref) "
					+ "VALUES (1,1,'Legacy scene','Keep description',CAST(? AS jsonb),'https://cdn.example.com/legacy.png')",
					"{\"visualizer\":{\"shader\":\"sphere(0.5);\"},\"state\":{\"size\":0.8}}");
			var before = this.jdbc.queryForMap("SELECT * FROM " + schema + ".scenes WHERE id = 1");

			Flyway.configure().dataSource(this.dataSource).schemas(schema).defaultSchema(schema)
					.locations("classpath:db/migration").load().migrate();

			var after = this.jdbc.queryForMap("SELECT * FROM " + schema + ".scenes WHERE id = 1");
			assertThat(after.remove("scene_mode")).isEqualTo(Scene.LEGACY_CUSTOM);
			assertThat(after).isEqualTo(before);
			assertThat(this.jdbc.queryForObject("SELECT count(*) FROM " + schema + ".scene_availability_controls", Long.class)).isZero();
			assertThat(this.jdbc.queryForObject("SELECT enabled FROM " + schema + ".custom_rendering_control WHERE id = 1", Boolean.class)).isFalse();
			assertThat(this.jdbc.queryForObject("SELECT changed_at IS NULL AND reason IS NULL AND changed_by_user_id IS NULL FROM "
					+ schema + ".custom_rendering_control WHERE id = 1", Boolean.class)).isTrue();
		} finally {
			// This schema exists only inside the disposable Testcontainers database.
			this.jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
		}
	}

	@Test
	void controlsRequireValidReferencesAndAuditedStateChangesAndAllowOnlyOneGlobalRow() throws Exception {
		User owner = user("owner");
		Scene scene = scene(owner);
		assertThatThrownBy(() -> this.jdbc.update(
				"INSERT INTO scene_availability_controls (scene_id,disabled) VALUES (?,TRUE)", scene.getId()))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> this.jdbc.update(
				"INSERT INTO scene_availability_controls (scene_id,disabled) VALUES (?,FALSE)", Long.MAX_VALUE))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> this.jdbc.update("""
				INSERT INTO scene_availability_controls (scene_id,disabled,changed_at,reason)
				VALUES (?,TRUE,CURRENT_TIMESTAMP,'   ')
				""", scene.getId())).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> this.jdbc.update("INSERT INTO custom_rendering_control (id,enabled) VALUES (2,FALSE)"))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> this.jdbc.update("""
				UPDATE custom_rendering_control SET enabled = TRUE, changed_at = NULL,
				changed_by_user_id = NULL, reason = NULL WHERE id = 1
				""")).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> this.jdbc.update("""
				INSERT INTO scene_availability_controls (scene_id,disabled,changed_by_user_id,changed_at,reason)
				VALUES (?,TRUE,?,CURRENT_TIMESTAMP,'Invalid actor')
				""", scene.getId(), Long.MAX_VALUE)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void deletingAnOperatorPreservesTheDecisionAndDeletingTheSceneRemovesItsControl() throws Exception {
		User owner = user("owner");
		User operator = user("operator");
		Scene scene = scene(owner);
		this.jdbc.update("""
				INSERT INTO scene_availability_controls (scene_id,disabled,changed_by_user_id,changed_at,reason)
				VALUES (?,TRUE,?,CURRENT_TIMESTAMP,'Keep audit when actor is removed')
				""", scene.getId(), operator.getId());
		var changedAt = this.jdbc.queryForMap("SELECT changed_at FROM scene_availability_controls WHERE scene_id = ?", scene.getId());
		this.users.deleteById(operator.getId());
		var control = this.jdbc.queryForMap("SELECT * FROM scene_availability_controls WHERE scene_id = ?", scene.getId());
		assertThat(control.get("disabled")).isEqualTo(true);
		assertThat(control.get("changed_by_user_id")).isNull();
		assertThat(control.get("reason")).isEqualTo("Keep audit when actor is removed");
		assertThat(control.get("changed_at")).isEqualTo(changedAt.get("changed_at"));
		this.scenes.deleteById(scene.getId());
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM scene_availability_controls WHERE scene_id = ?",
				Long.class, scene.getId())).isZero();
	}

	private User user(String label) {
		return this.users.saveAndFlush(new User("r02-migration-" + label + "-" + System.nanoTime() + "@example.com", "test-hash", label));
	}

	private Scene scene(User owner) throws Exception {
		return this.scenes.saveAndFlush(new Scene(owner.getId(), "Migration test",
				new ObjectMapper().readTree("{\"visualizer\":{\"shader\":\"sphere(0.5);\"}}")));
	}
}
