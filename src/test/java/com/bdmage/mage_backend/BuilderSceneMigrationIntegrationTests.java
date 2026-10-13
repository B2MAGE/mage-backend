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
class BuilderSceneMigrationIntegrationTests extends PostgresIntegrationTestSupport {
	@Autowired private DataSource dataSource;
	@Autowired private JdbcTemplate jdbc;

	@Test
	void personalTemplateMigrationKeepsDesignsAndImagesWithoutChangingScenes() {
		String schema = "sb14_design_" + System.nanoTime();
		try {
			Flyway.configure().dataSource(this.dataSource).schemas(schema).defaultSchema(schema)
					.locations("classpath:db/migration").target("23").load().migrate();
			this.jdbc.update("INSERT INTO " + schema + ".users "
					+ "(id,email,password_hash,display_name,first_name,last_name,handle) "
					+ "VALUES (1,'design@example.test','unused','Design','Design','','design')");
			String design = "{\"schemaVersion\":1,\"kind\":\"builder\",\"builderVersion\":1,\"objects\":[]}";
			String fullScene = design.substring(0, design.length() - 1)
					+ ",\"parameters\":{\"scale\":0.8},\"settings\":{\"skybox\":6}}";
			this.jdbc.update("INSERT INTO " + schema + ".builder_templates "
					+ "(id,owner_user_id,name,scene_data,thumbnail_ref,revision,updated_at) "
					+ "VALUES (1,1,'Keep design',?::jsonb,'keep.png',7,'2026-10-01T00:00:00Z'),"
					+ "(2,1,'Already design only',?::jsonb,'also-keep.png',4,'2026-10-01T00:00:00Z')", fullScene, design);
			this.jdbc.update("INSERT INTO " + schema + ".scenes "
					+ "(id,owner_user_id,name,scene_data,thumbnail_ref,scene_mode) "
					+ "VALUES (1,1,'Keep entire scene',?::jsonb,'scene.png','builder-v1')", fullScene);
			var templateBefore = this.jdbc.queryForMap("SELECT * FROM " + schema + ".builder_templates WHERE id=1");
			var existingDesignBefore = this.jdbc.queryForMap("SELECT * FROM " + schema + ".builder_templates WHERE id=2");
			var sceneBefore = this.jdbc.queryForMap("SELECT * FROM " + schema + ".scenes WHERE id=1");
			Flyway.configure().dataSource(this.dataSource).schemas(schema).defaultSchema(schema)
					.locations("classpath:db/migration").load().migrate();
			var templateAfter = this.jdbc.queryForMap("SELECT * FROM " + schema + ".builder_templates WHERE id=1");
			assertThat(templateAfter.remove("revision")).isEqualTo(8L);
			templateBefore.remove("revision");
			assertThat(templateAfter.remove("updated_at")).isNotEqualTo(templateBefore.remove("updated_at"));
			templateAfter.remove("scene_data");
			templateBefore.remove("scene_data");
			assertThat(templateAfter).isEqualTo(templateBefore);
			assertThat(this.jdbc.queryForObject("SELECT scene_data = ?::jsonb FROM " + schema + ".builder_templates WHERE id=1", Boolean.class, design)).isTrue();
			assertThat(this.jdbc.queryForMap("SELECT * FROM " + schema + ".builder_templates WHERE id=2")).isEqualTo(existingDesignBefore);
			assertThat(this.jdbc.queryForMap("SELECT * FROM " + schema + ".scenes WHERE id=1")).isEqualTo(sceneBefore);
			assertThatThrownBy(() -> this.jdbc.update("UPDATE " + schema + ".builder_templates SET scene_data=?::jsonb WHERE id=1", fullScene))
					.isInstanceOf(DataIntegrityViolationException.class);
		} finally {
			this.jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
		}
	}

	@Test
	void initializesEditorRevisionsWithoutChangingExistingScenes() {
		String schema = "sb08_revision_" + System.nanoTime();
		try {
			Flyway.configure().dataSource(this.dataSource).schemas(schema).defaultSchema(schema)
					.locations("classpath:db/migration").target("21").load().migrate();
			this.jdbc.update("INSERT INTO " + schema + ".users "
					+ "(id,email,password_hash,display_name,first_name,last_name,handle) "
					+ "VALUES (1,'revision@example.test','unused','Revision','Revision','','revision')");
			this.jdbc.update("INSERT INTO " + schema + ".scenes "
					+ "(id,owner_user_id,name,description,scene_data,thumbnail_ref,scene_mode) "
					+ "VALUES (1,1,'Keep name','Keep description',CAST(? AS jsonb),'keep.png','builder-v1')",
					"{\"schemaVersion\":1,\"kind\":\"builder\",\"builderVersion\":1,\"objects\":[]}");
			var before = this.jdbc.queryForMap("SELECT * FROM " + schema + ".scenes WHERE id=1");
			Flyway.configure().dataSource(this.dataSource).schemas(schema).defaultSchema(schema)
					.locations("classpath:db/migration").load().migrate();
			var after = this.jdbc.queryForMap("SELECT * FROM " + schema + ".scenes WHERE id=1");
			assertThat(after.remove("revision")).isEqualTo(0L);
			assertThat(after).isEqualTo(before);
			assertThatThrownBy(() -> this.jdbc.update("UPDATE " + schema + ".scenes SET revision=-1 WHERE id=1"))
					.isInstanceOf(DataIntegrityViolationException.class);
		} finally {
			this.jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
		}
	}

	@Test
	void addsBuilderClassificationWithoutRewritingOrPromotingAnyExistingDocument() {
		String schema = "sb01_upgrade_" + System.nanoTime();
		try {
			Flyway.configure().dataSource(this.dataSource).schemas(schema).defaultSchema(schema)
					.locations("classpath:db/migration").target("20").load().migrate();
			this.jdbc.update("INSERT INTO " + schema + ".users "
					+ "(id,email,password_hash,display_name,first_name,last_name,handle) "
					+ "VALUES (1,'builder@example.com','hash','Builder','Builder','','builder')");
			String builder = "{\"schemaVersion\":1,\"kind\":\"builder\",\"builderVersion\":1,\"objects\":[]}";
			String[] documents = {
					"{\"visualizer\":{\"shader\":\"sphere(1)\"},\"keepLegacy\":true}",
					"{\"schemaVersion\":1,\"kind\":\"custom\",\"scene\":{\"visualizer\":{\"shader\":\"sphere(1)\"}}}",
					"{\"schemaVersion\":1,\"kind\":\"template\",\"templateId\":\"embedded-scene-0\",\"templateVersion\":1}",
					builder,
			};
			String[] modes = { "legacy-custom", "custom-v1", "template-v1", "legacy-custom" };
			for (int index = 0; index < documents.length; index++) {
				this.jdbc.update("INSERT INTO " + schema + ".scenes "
						+ "(id,owner_user_id,name,description,scene_data,thumbnail_ref,scene_mode) "
						+ "VALUES (?,1,'Keep name','Keep description',CAST(? AS jsonb),'keep.png',?)",
						index + 1, documents[index], modes[index]);
			}
			var before = this.jdbc.queryForList("SELECT * FROM " + schema + ".scenes ORDER BY id");
			Flyway.configure().dataSource(this.dataSource).schemas(schema).defaultSchema(schema)
					.locations("classpath:db/migration").target("21").load().migrate();
			assertThat(this.jdbc.queryForList("SELECT * FROM " + schema + ".scenes ORDER BY id")).isEqualTo(before);
			this.jdbc.update("UPDATE " + schema + ".scenes SET scene_mode='builder-v1' WHERE id=4");
			for (String invalid : new String[] {
					documents[0], documents[1], documents[2],
					"{\"schemaVersion\":1,\"kind\":\"builder\",\"builderVersion\":2,\"objects\":[]}",
					"{\"schemaVersion\":2,\"kind\":\"builder\",\"builderVersion\":1,\"objects\":[]}",
					"{\"schemaVersion\":1,\"kind\":\"builder\",\"builderVersion\":1}",
					"{\"schemaVersion\":1,\"kind\":\"builder\",\"builderVersion\":1,\"objects\":null}",
			}) {
				assertThatThrownBy(() -> this.jdbc.update("UPDATE " + schema + ".scenes SET scene_data=?::jsonb WHERE id=4", invalid))
						.isInstanceOf(DataIntegrityViolationException.class);
			}
			assertThatThrownBy(() -> this.jdbc.update("UPDATE " + schema + ".scenes SET scene_mode='custom-v1' WHERE id=4"))
					.isInstanceOf(DataIntegrityViolationException.class);
			assertThatThrownBy(() -> this.jdbc.update("UPDATE " + schema + ".scenes SET scene_mode='template-v1' WHERE id=4"))
					.isInstanceOf(DataIntegrityViolationException.class);
		} finally {
			// This generated schema exists only inside the disposable integration database.
			this.jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
		}
	}
}
