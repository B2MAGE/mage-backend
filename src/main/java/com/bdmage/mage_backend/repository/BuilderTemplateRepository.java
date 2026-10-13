package com.bdmage.mage_backend.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import com.fasterxml.jackson.core.type.TypeReference;
import com.bdmage.mage_backend.dto.BuilderTemplateResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BuilderTemplateRepository {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> DOCUMENT_TYPE = new TypeReference<>() { };
    private final JdbcTemplate jdbc;

    public BuilderTemplateRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<BuilderTemplateResponse> list(Long owner) {
        return this.jdbc.query("SELECT * FROM builder_templates WHERE owner_user_id = ? ORDER BY lower(name), id",
                BuilderTemplateRepository::read, owner);
    }

    public Optional<BuilderTemplateResponse> find(Long owner, Long id, boolean lock) {
        return this.jdbc.query("SELECT * FROM builder_templates WHERE owner_user_id = ? AND id = ?"
                + (lock ? " FOR UPDATE" : ""), BuilderTemplateRepository::read, owner, id).stream().findFirst();
    }

    public BuilderTemplateResponse create(Long owner, String name, JsonNode document) {
        return this.jdbc.queryForObject("""
                INSERT INTO builder_templates(owner_user_id, name, scene_data) VALUES (?, ?, ?::jsonb) RETURNING *
                """, BuilderTemplateRepository::read, owner, name, document.toString());
    }

    public BuilderTemplateResponse update(Long id, String name, JsonNode document, String thumbnail) {
        return this.jdbc.queryForObject("""
                UPDATE builder_templates SET name = ?, scene_data = ?::jsonb, thumbnail_ref = ?,
                    revision = revision + 1, updated_at = clock_timestamp() WHERE id = ? RETURNING *
                """, BuilderTemplateRepository::read, name, document.toString(), thumbnail, id);
    }

    public BuilderTemplateResponse setInitialThumbnail(Long id, String thumbnail) {
        return this.jdbc.queryForObject("UPDATE builder_templates SET thumbnail_ref = ? WHERE id = ? RETURNING *",
                BuilderTemplateRepository::read, thumbnail, id);
    }

    public void delete(Long id) { this.jdbc.update("DELETE FROM builder_templates WHERE id = ?", id); }

    public void registerUpload(Long owner, String key, Instant expiry) {
        this.jdbc.update("INSERT INTO builder_template_uploads(object_key, owner_user_id, expires_at) VALUES (?, ?, ?)",
                key, owner, Timestamp.from(expiry));
    }

    public boolean consumeUpload(Long owner, String key, boolean requireUnexpired) {
        return this.jdbc.update("""
                UPDATE builder_template_uploads SET consumed = TRUE
                WHERE object_key = ? AND owner_user_id = ? AND consumed = FALSE
                """ + (requireUnexpired ? " AND expires_at > CURRENT_TIMESTAMP" : ""), key, owner) == 1;
    }

    private static BuilderTemplateResponse read(ResultSet row, int index) throws SQLException {
        try {
            return new BuilderTemplateResponse(row.getLong("id"), row.getString("name"),
                    JSON.readValue(row.getString("scene_data"), DOCUMENT_TYPE), row.getString("thumbnail_ref"),
                    row.getLong("revision"), row.getTimestamp("created_at").toInstant(), row.getTimestamp("updated_at").toInstant());
        } catch (java.io.IOException ex) {
            throw new SQLException("Stored Builder template document is invalid", ex);
        }
    }
}
