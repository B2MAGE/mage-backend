package com.bdmage.mage_backend.repository;

import java.util.Map;
import java.util.Optional;

import com.bdmage.mage_backend.model.CustomRenderingControl;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CustomRenderingControlRepository {

	private final NamedParameterJdbcTemplate jdbc;

	public CustomRenderingControlRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public Optional<CustomRenderingControl> findCurrent() {
		return this.jdbc.query("""
				SELECT enabled, changed_by_user_id, changed_at, reason
				FROM custom_rendering_control WHERE id = 1
				""", Map.of(), (row, index) -> {
					var changedAt = row.getTimestamp("changed_at");
					return new CustomRenderingControl(row.getBoolean("enabled"),
							row.getObject("changed_by_user_id", Long.class),
							changedAt == null ? null : changedAt.toInstant(), row.getString("reason"));
				}).stream().findFirst();
	}

	public void setEnabled(boolean enabled, Long actorId, String reason) {
		this.jdbc.update("""
				INSERT INTO custom_rendering_control
				    (id, enabled, changed_by_user_id, changed_at, reason)
				VALUES (1, :enabled,
				        CASE WHEN :enabled THEN :actorId ELSE NULL END,
				        CASE WHEN :enabled THEN clock_timestamp() ELSE NULL END,
				        CASE WHEN :enabled THEN :reason ELSE NULL END)
				ON CONFLICT (id) DO UPDATE SET
				    enabled = EXCLUDED.enabled, changed_by_user_id = :actorId,
				    changed_at = clock_timestamp(), reason = :reason
				WHERE custom_rendering_control.enabled IS DISTINCT FROM EXCLUDED.enabled
				""", Map.of("enabled", enabled, "actorId", actorId, "reason", reason));
	}
}
