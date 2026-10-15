package com.bdmage.mage_backend.repository;

import java.time.Instant;
import java.util.List;

import com.bdmage.mage_backend.service.SceneDiscoveryQuery;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SceneDiscoveryRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public SceneDiscoveryRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public long count(SceneDiscoveryQuery query) {
        return this.jdbc.queryForObject("SELECT COUNT(*) FROM scenes s" + tagFilter(query, "s"),
                parameters(query), Long.class);
    }

    public List<Metadata> findPage(SceneDiscoveryQuery query) {
        // Select no scene_data and never hydrate a Scene entity, including for ranked results.
        String rankJoin = "";
        String order = "s.created_at DESC, s.id DESC";
        switch (query.sort()) {
            case ASCENDING -> order = "s.created_at ASC, s.id ASC";
            case MOST_VIEWED, MOST_LIKED -> {
                String table = query.sort() == SceneDiscoveryQuery.Sort.MOST_VIEWED ? "scene_views" : "scene_votes";
                String positiveVotes = query.sort() == SceneDiscoveryQuery.Sort.MOST_LIKED
                        ? (query.tag() == null ? " WHERE" : " AND") + " a.vote_value = 1" : "";
                // Rank the complete filtered collection before applying LIMIT, not just the returned page.
                rankJoin = " LEFT JOIN (SELECT a.scene_id, COUNT(*) AS rank_count FROM " + table + " a"
                        + " JOIN scenes ranked_scene ON ranked_scene.id = a.scene_id"
                        + tagFilter(query, "ranked_scene") + positiveVotes
                        + " GROUP BY a.scene_id) ranking ON ranking.scene_id = s.id";
                order = "COALESCE(ranking.rank_count, 0) DESC, s.created_at DESC, s.id DESC";
            }
            default -> { /* Featured and recommended currently use the same newest-first order. */ }
        }
        String sql = """
                SELECT s.id, s.owner_user_id, s.name, s.description, s.thumbnail_ref, s.created_at,
                       u.display_name, u.handle, u.avatar_gradient_start, u.avatar_gradient_end
                FROM scenes s JOIN users u ON u.id = s.owner_user_id
                """ + rankJoin + tagFilter(query, "s") + " ORDER BY " + order + " LIMIT :size OFFSET :offset";
        return this.jdbc.query(sql, parameters(query), (row, index) -> new Metadata(
                row.getLong("id"), row.getLong("owner_user_id"), row.getString("display_name"),
                row.getString("handle"), row.getString("avatar_gradient_start"), row.getString("avatar_gradient_end"),
                row.getString("name"), row.getString("description"), row.getString("thumbnail_ref"),
                row.getTimestamp("created_at").toInstant()));
    }

    private static String tagFilter(SceneDiscoveryQuery query, String sceneAlias) {
        return query.tag() == null ? "" : " WHERE EXISTS (SELECT 1 FROM scene_tags st JOIN tags t ON t.id = st.tag_id"
                + " WHERE st.scene_id = " + sceneAlias + ".id AND t.name = :tag)";
    }

    private static MapSqlParameterSource parameters(SceneDiscoveryQuery query) {
        return new MapSqlParameterSource().addValue("tag", query.tag()).addValue("size", query.size())
                .addValue("offset", query.offset());
    }

    public record Metadata(Long sceneId, Long ownerUserId, String creatorDisplayName, String creatorHandle,
            String creatorAvatarGradientStart, String creatorAvatarGradientEnd, String name, String description,
            String thumbnailRef, Instant createdAt) {}
}
