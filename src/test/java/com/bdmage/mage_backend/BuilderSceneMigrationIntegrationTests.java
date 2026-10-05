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
					.locations("classpath:db/migration").load().migrate();
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
