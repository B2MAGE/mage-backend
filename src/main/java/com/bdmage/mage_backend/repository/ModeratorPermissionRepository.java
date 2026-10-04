package com.bdmage.mage_backend.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import com.bdmage.mage_backend.dto.ModeratorAuditResponse;
import com.bdmage.mage_backend.dto.ModeratorUserResponse;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ModeratorPermissionRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private static final String USER_SELECT = """
        SELECT u.id, u.display_name, u.handle, u.email,
               COALESCE(p.enabled, FALSE) AS enabled, COALESCE(p.revision, 0) AS revision
        FROM users u LEFT JOIN scene_moderator_permissions p ON p.user_id = u.id
        """;

    public ModeratorPermissionRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean isModerator(long userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM scene_moderator_permissions WHERE user_id=:id AND enabled)", Map.of("id", userId), Boolean.class));
    }

    public Optional<ModeratorUserResponse> findUser(long id) {
        return jdbc.query(USER_SELECT + " WHERE u.id=:id", Map.of("id", id), ModeratorPermissionRepository::user).stream().findFirst();
    }

    public List<ModeratorUserResponse> lookup(String identifier, boolean email) {
        return jdbc.query(USER_SELECT + (email ? " WHERE u.email=:query" : " WHERE u.handle=:query") + " LIMIT 1",
                Map.of("query", identifier), ModeratorPermissionRepository::user);
    }

    /** Serialize one administrator's UUID namespace, including requests for different targets. */
    public void lockAdministrator(long id) {
        jdbc.query("SELECT pg_advisory_xact_lock(:id)", Map.of("id", id), (row, index) -> true);
    }

    /** Lock the existing user so concurrent creation of a version-zero grant is safe. */
    public boolean lockUser(long id) {
        return !jdbc.query("SELECT id FROM users WHERE id=:id FOR UPDATE", Map.of("id", id), (row, index) -> row.getLong(1)).isEmpty();
    }

    public record Replay(long targetId, boolean enabled, long expectedRevision, String reason, ModeratorUserResponse response) {}
    public Optional<Replay> replay(long actorId, UUID requestId) {
        return jdbc.query("""
            SELECT * FROM scene_moderator_audit WHERE administrator_user_id=:actor AND request_id=:request
            """, Map.of("actor", actorId, "request", requestId), (row, index) -> new Replay(row.getLong("target_user_id"),
                row.getBoolean("enabled"), row.getLong("expected_revision"), row.getString("reason"),
                new ModeratorUserResponse(row.getLong("target_user_id"), row.getString("display_name"),
                    row.getString("handle"), row.getString("email"), row.getBoolean("enabled"), row.getLong("revision"), false))).stream().findFirst();
    }

    public void save(long id, boolean enabled, long revision) {
        jdbc.update("""
            INSERT INTO scene_moderator_permissions (user_id, enabled, revision) VALUES (:id,:enabled,:revision)
            ON CONFLICT (user_id) DO UPDATE SET enabled=EXCLUDED.enabled, revision=EXCLUDED.revision
            """, Map.of("id", id, "enabled", enabled, "revision", revision));
    }

    public void appendAudit(Long administrator, ModeratorUserResponse before, ModeratorUserResponse after,
            UUID requestId, String reason, String source) {
        jdbc.update("""
            INSERT INTO scene_moderator_audit
            (administrator_user_id,target_user_id,previous_enabled,enabled,expected_revision,revision,reason,request_id,source,display_name,handle,email)
            VALUES (:actor,:target,:previous,:enabled,:expected,:revision,:reason,:request,:source,:name,:handle,:email)
            """, new MapSqlParameterSource().addValue("actor", administrator).addValue("target", before.userId())
                .addValue("previous", before.sceneModerator()).addValue("enabled", after.sceneModerator())
                .addValue("expected", before.revision()).addValue("revision", after.revision()).addValue("reason", reason)
                .addValue("request", requestId).addValue("source", source).addValue("name", after.displayName())
                .addValue("handle", after.handle()).addValue("email", after.email()));
    }

    public List<ModeratorAuditResponse> audit(Long beforeId, int limit) {
        return jdbc.query("SELECT * FROM scene_moderator_audit" + (beforeId == null ? "" : " WHERE id < :before") + " ORDER BY id DESC LIMIT :limit",
                new MapSqlParameterSource().addValue("before", beforeId).addValue("limit", limit),
                (row, index) -> new ModeratorAuditResponse(row.getLong("id"), row.getObject("administrator_user_id", Long.class),
                    row.getLong("target_user_id"), row.getBoolean("previous_enabled"), row.getBoolean("enabled"),
                    row.getLong("revision"), row.getString("reason"), row.getObject("request_id", UUID.class),
                    row.getTimestamp("changed_at").toInstant(), row.getString("source")));
    }

    public boolean lockBootstrapCompleted() {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT completed_at IS NOT NULL FROM scene_moderator_bootstrap WHERE id=1 FOR UPDATE", Map.of(), Boolean.class));
    }

    public boolean hasGrantRow(long id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM scene_moderator_permissions WHERE user_id=:id)", Map.of("id", id), Boolean.class));
    }

    public void completeBootstrap() {
        jdbc.update("UPDATE scene_moderator_bootstrap SET completed_at=clock_timestamp() WHERE id=1", Map.of());
    }

    private static ModeratorUserResponse user(ResultSet row, int index) throws SQLException {
        return new ModeratorUserResponse(row.getLong("id"), row.getString("display_name"), row.getString("handle"),
                row.getString("email"), row.getBoolean("enabled"), row.getLong("revision"), false);
    }
}
