package com.bdmage.mage_backend;

import javax.sql.DataSource;

import com.bdmage.mage_backend.support.PostgresIntegrationTestSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
class UserAvatarGradientMigrationIntegrationTests extends PostgresIntegrationTestSupport {

	@Autowired private DataSource dataSource;

	@Test
	void migrationBackfillsExistingUsersAndEnforcesSafeDefaults() {
		// This schema is contained in the disposable test database, never the running app database.
		String schema = "avatar_backfill_" + Long.toUnsignedString(System.nanoTime(), 36);
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		jdbc.execute("CREATE SCHEMA " + schema);
		Flyway.configure().dataSource(dataSource).defaultSchema(schema).schemas(schema).target("16").load().migrate();
		String insert = "INSERT INTO " + schema + ".users (email, password_hash, first_name, last_name, display_name, handle) "
				+ "VALUES (?, 'hash', 'Test', 'User', 'Test User', ?)";
		jdbc.update(insert, "existing@example.com", "existing_user");
		Flyway.configure().dataSource(dataSource).defaultSchema(schema).schemas(schema).load().migrate();
		jdbc.update(insert, "new@example.com", "new_user");
		assertThat(jdbc.queryForList("SELECT avatar_gradient_start FROM " + schema + ".users", String.class))
				.containsExactly("#5c51ba", "#5c51ba");
		assertThat(jdbc.queryForList("SELECT avatar_gradient_end FROM " + schema + ".users", String.class))
				.containsExactly("#264a48", "#264a48");
		assertThatThrownBy(() -> jdbc.update("UPDATE " + schema + ".users SET avatar_gradient_start = 'invalid'"))
				.isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("UPDATE " + schema + ".users SET avatar_gradient_end = NULL"))
				.isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
	}
}
