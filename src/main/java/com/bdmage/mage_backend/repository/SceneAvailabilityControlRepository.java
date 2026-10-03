package com.bdmage.mage_backend.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.bdmage.mage_backend.model.SceneAvailabilityControl;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SceneAvailabilityControlRepository {

	private final NamedParameterJdbcTemplate jdbc;

	public SceneAvailabilityControlRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	// Read only IDs and controls: availability checks must not fetch shader source.
	public List<SceneAvailabilityControl> findForExistingSceneIds(Collection<Long> sceneIds) {
		if (sceneIds.isEmpty()) return List.of();
		return this.jdbc.query("""
				SELECT s.id AS scene_id, COALESCE(c.disabled, FALSE) AS disabled,
				       c.changed_by_user_id, c.changed_at, c.reason, s.scene_mode
				FROM scenes s LEFT JOIN scene_availability_controls c ON c.scene_id = s.id
				WHERE s.id IN (:sceneIds)
				""", Map.of("sceneIds", sceneIds), SceneAvailabilityControlRepository::mapControl);
	}

	public void setDisabled(Long sceneId, boolean disabled, Long actorId, String reason) {
		// PostgreSQL locks conflicting rows. Retrying the same target state keeps its
		// original audit, while both true -> false and false -> true record the actor.
		this.jdbc.update("""
				INSERT INTO scene_availability_controls
				    (scene_id, disabled, changed_by_user_id, changed_at, reason)
				VALUES (:sceneId, :disabled,
				        CASE WHEN :disabled THEN :actorId ELSE NULL END,
				        CASE WHEN :disabled THEN clock_timestamp() ELSE NULL END,
				        CASE WHEN :disabled THEN :reason ELSE NULL END)
				ON CONFLICT (scene_id) DO UPDATE SET
				    disabled = EXCLUDED.disabled, changed_by_user_id = :actorId,
				    changed_at = clock_timestamp(), reason = :reason
				WHERE scene_availability_controls.disabled IS DISTINCT FROM EXCLUDED.disabled
				""", Map.of("sceneId", sceneId, "disabled", disabled, "actorId", actorId, "reason", reason));
	}

	private static SceneAvailabilityControl mapControl(ResultSet row, int index) throws SQLException {
		var changedAt = row.getTimestamp("changed_at");
		return new SceneAvailabilityControl(row.getLong("scene_id"), row.getBoolean("disabled"),
				row.getObject("changed_by_user_id", Long.class),
				changedAt == null ? null : changedAt.toInstant(), row.getString("reason"), row.getString("scene_mode"));
	}
}
