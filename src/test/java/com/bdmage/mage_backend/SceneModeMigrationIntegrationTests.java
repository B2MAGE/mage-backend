package com.bdmage.mage_backend;

import javax.sql.DataSource;

import com.bdmage.mage_backend.support.PostgresIntegrationTestSupport;
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
class SceneModeMigrationIntegrationTests extends PostgresIntegrationTestSupport {
	@Autowired private DataSource dataSource;
	@Autowired private JdbcTemplate jdbc;

	@Test
	void migrationPreservesEvenClaimedTemplateDocumentsAndDoesNotGrantTrust() {
		String schema = "b02_upgrade_" + System.nanoTime();
		try {
			Flyway.configure().dataSource(this.dataSource).schemas(schema).defaultSchema(schema)
					.locations("classpath:db/migration").target("18").load().migrate();
			this.jdbc.update("INSERT INTO " + schema + ".users "
					+ "(id,email,password_hash,display_name,first_name,last_name,handle) "
					+ "VALUES (1,'legacy@example.com','hash','Legacy','Legacy','','legacy')");
			String[] documents = {
					"{\"visualizer\":{\"shader\":\"sphere(0.5);\"},\"unsupportedOldSetting\":true}",
					"{\"schemaVersion\":1,\"kind\":\"template\",\"templateId\":\"embedded-scene-0\",\"templateVersion\":1}",
					"{\"schemaVersion\":1,\"kind\":\"custom\",\"scene\":{\"visualizer\":{\"shader\":\"sphere(0.5);\"}}}"
			};
			for (int i = 0; i < documents.length; i++) {
				this.jdbc.update("INSERT INTO " + schema + ".scenes (id,owner_user_id,name,description,scene_data,thumbnail_ref) "
						+ "VALUES (?,1,'Original name','Original description',CAST(? AS jsonb),'original.png')", i + 1, documents[i]);
			}
			var before = this.jdbc.queryForList("SELECT * FROM " + schema + ".scenes ORDER BY id");
			Flyway.configure().dataSource(this.dataSource).schemas(schema).defaultSchema(schema)
					.locations("classpath:db/migration").load().migrate();
			var after = this.jdbc.queryForList("SELECT * FROM " + schema + ".scenes ORDER BY id");
			after.forEach(row -> assertThat(row.remove("scene_mode")).isEqualTo("legacy-custom"));
			assertThat(after).isEqualTo(before);

			// An old writer cannot leave a trusted marker attached to a replacement raw document.
			assertThatThrownBy(() -> this.jdbc.update("UPDATE " + schema + ".scenes SET scene_mode='template-v1' WHERE id=1"))
					.isInstanceOf(DataIntegrityViolationException.class);
			assertThatThrownBy(() -> this.jdbc.update("UPDATE " + schema + ".scenes SET scene_mode='unknown' WHERE id=2"))
					.isInstanceOf(DataIntegrityViolationException.class);
			assertThatThrownBy(() -> this.jdbc.update("UPDATE " + schema + ".scenes SET scene_mode='custom-v1', "
					+ "scene_data='{\"schemaVersion\":1,\"kind\":\"custom\"}'::jsonb WHERE id=3"))
					.isInstanceOf(DataIntegrityViolationException.class);
			this.jdbc.update("UPDATE " + schema + ".scenes SET scene_mode='template-v1' WHERE id=2");
			assertThatThrownBy(() -> this.jdbc.update("UPDATE " + schema + ".scenes SET scene_data=?::jsonb WHERE id=2", documents[0]))
					.isInstanceOf(DataIntegrityViolationException.class);
		} finally {
			// This generated schema is confined to the disposable Testcontainers database.
			this.jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
		}
	}
}
