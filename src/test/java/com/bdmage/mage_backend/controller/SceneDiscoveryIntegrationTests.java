package com.bdmage.mage_backend.controller;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.repository.UserRepository;
import com.bdmage.mage_backend.service.AuthenticationTokenService;
import com.bdmage.mage_backend.support.PostgresIntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Disposable PostgreSQL fixtures: these tests do not use the local development database. */
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.stat=OFF",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"
})
@AutoConfigureMockMvc
@Transactional
@Testcontainers
class SceneDiscoveryIntegrationTests extends PostgresIntegrationTestSupport {
    private static final String PATH = "/api/scenes/discovery";
    private static final String DOCUMENT = """
            {"schemaVersion":1,"kind":"template","templateId":"embedded-scene-0","templateVersion":1}
            """;
    private final ObjectMapper json = new ObjectMapper();
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository users;
    @Autowired private AuthenticationTokenService tokens;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;
    private User owner;

    @BeforeEach
    void createOwner() {
        this.owner = user("creator");
    }

    @Test
    void zeroResultsAndDefaultsHaveAnExplicitPageContract() throws Exception {
        JsonNode result = request(get(PATH));
        assertThat(result.path("items").isArray()).isTrue();
        assertThat(result.path("items").isEmpty()).isTrue();
        assertThat(result.path("page").asInt()).isZero();
        assertThat(result.path("size").asInt()).isEqualTo(12);
        assertThat(result.path("totalElements").asLong()).isZero();
        assertThat(result.path("totalPages").asLong()).isZero();
    }

    @Test
    void pagesBoundTheResultsAndUseStableTiesWithNoMissingOrRepeatedScenes() throws Exception {
        List<Long> ids = new ArrayList<>();
        for (int index = 0; index < 27; index++) ids.add(scene("Design " + index, "2026-10-01T12:00:00Z"));

        JsonNode first = request(get(PATH));
        JsonNode second = request(get(PATH).param("page", "1"));
        JsonNode last = request(get(PATH).param("page", "2"));
        assertPage(first, 0, 12, 27, 3);
        assertPage(second, 1, 12, 27, 3);
        assertPage(last, 2, 12, 27, 3);
        assertThat(first.path("items").size()).isEqualTo(12);
        assertThat(second.path("items").size()).isEqualTo(12);
        assertThat(last.path("items").size()).isEqualTo(3);
        List<Long> combined = new ArrayList<>(ids(first));
        combined.addAll(ids(second));
        combined.addAll(ids(last));
        assertThat(combined).containsExactlyElementsOf(ids.reversed());
        assertThat(ids(request(get(PATH)))).containsExactlyElementsOf(ids(first));

        JsonNode outside = request(get(PATH).param("page", "3"));
        assertPage(outside, 3, 12, 27, 3);
        assertThat(outside.path("items").isEmpty()).isTrue();
        JsonNode farOutside = request(get(PATH).param("page", String.valueOf(Integer.MAX_VALUE)).param("size", "48"));
        assertPage(farOutside, Integer.MAX_VALUE, 48, 27, 1);
        assertThat(farOutside.path("items").isEmpty()).isTrue();
    }

    @Test
    void allSortsApplyToTheWholeFilteredCollectionBeforeTakingThePage() throws Exception {
        long oldest = scene("Oldest with most likes", "2026-01-01T00:00:00Z");
        long newer = scene("Newer with most views", "2026-02-01T00:00:00Z");
        long tiedFirst = scene("Tie first", "2026-03-01T00:00:00Z");
        long tiedLast = scene("Tie last", "2026-03-01T00:00:00Z");
        User second = user("second");
        vote(oldest, this.owner.getId(), 1);
        vote(oldest, second.getId(), 1);
        vote(newer, this.owner.getId(), -1);
        vote(tiedFirst, this.owner.getId(), 1);
        vote(tiedLast, this.owner.getId(), 1);
        for (int index = 0; index < 3; index++) view(newer);
        view(tiedFirst);
        view(tiedLast);

        for (String sort : List.of("descending", "featured", "recommended")) {
            assertThat(ids(request(get(PATH).param("sort", sort))))
                    .containsExactly(tiedLast, tiedFirst, newer, oldest);
        }
        assertThat(ids(request(get(PATH).param("sort", "ascending"))))
                .containsExactly(oldest, newer, tiedFirst, tiedLast);
        assertThat(ids(request(get(PATH).param("sort", "most-viewed"))))
                .containsExactly(newer, tiedLast, tiedFirst, oldest);
        assertThat(ids(request(get(PATH).param("sort", "most-liked"))))
                .containsExactly(oldest, tiedLast, tiedFirst, newer);
        assertThat(ids(request(get(PATH).param("sort", "most-viewed").param("size", "1"))))
                .containsExactly(newer);
        assertThat(ids(request(get(PATH).param("sort", "most-liked").param("size", "1").param("page", "1"))))
                .containsExactly(tiedLast);

        tag(oldest, "fire");
        tag(tiedFirst, "fire");
        tag(tiedLast, "fire");
        JsonNode filtered = request(get(PATH).param("sort", "most-viewed").param("tag", "fire").param("size", "1"));
        assertPage(filtered, 0, 1, 3, 3);
        assertThat(ids(filtered)).containsExactly(tiedLast);
        assertThat(ids(request(get(PATH).param("sort", "most-liked").param("tag", "fire").param("size", "1"))))
                .containsExactly(oldest);
    }

    @Test
    void tagFilteringNormalizesNamesAndCountsScenesOnceWithSeveralTags() throws Exception {
        long tagged = scene("Tagged", "2026-01-01T00:00:00Z");
        long other = scene("Other", "2026-02-01T00:00:00Z");
        tag(tagged, "fire");
        tag(tagged, "water");
        tag(other, "water");
        JsonNode filtered = request(get(PATH).param("tag", " FIRE ").param("size", "1"));
        assertPage(filtered, 0, 1, 1, 1);
        assertThat(ids(filtered)).containsExactly(tagged);
        assertThat(filtered.path("items").get(0).path("tags").toString()).isEqualTo("[\"fire\",\"water\"]");
        assertThat(request(get(PATH).param("tag", "missing")).path("items").isEmpty()).isTrue();
        assertThat(request(get(PATH).param("tag", "   ")).path("totalElements").asLong()).isEqualTo(2);
        assertThat(request(get(PATH).param("tag", "fire' OR 1=1 --")).path("items").isEmpty()).isTrue();
    }

    @Test
    void invalidParametersAreRejectedRatherThanAllowingUnboundedRequests() throws Exception {
        for (String[] input : List.of(new String[]{"page", "-1"}, new String[]{"page", "no"},
                new String[]{"page", "2147483648"}, new String[]{"size", "0"}, new String[]{"size", "49"},
                new String[]{"size", "-1"}, new String[]{"size", "1.5"}, new String[]{"sort", "unknown"},
                new String[]{"sort", "descending; DROP TABLE scenes"}, new String[]{"tag", "x".repeat(65)})) {
            this.mvc.perform(get(PATH).param(input[0], input[1]))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        for (int index = 0; index < 50; index++) scene("Design " + index, "2026-01-01T00:00:00Z");
        JsonNode maximum = request(get(PATH).param("size", "48"));
        assertPage(maximum, 0, 48, 50, 2);
        assertThat(maximum.path("items").size()).isEqualTo(48);
    }

    @Test
    void deletedRowsLeaveAnEmptyOutOfRangePageWithUpdatedTotals() throws Exception {
        long first = scene("First", "2026-01-01T00:00:00Z");
        long middle = scene("Middle", "2026-02-01T00:00:00Z");
        scene("Last", "2026-03-01T00:00:00Z");
        assertThat(ids(request(get(PATH).param("page", "1").param("size", "2")))).containsExactly(first);
        this.jdbc.update("DELETE FROM scenes WHERE id = ?", middle);
        JsonNode after = request(get(PATH).param("page", "1").param("size", "2"));
        assertPage(after, 1, 2, 2, 1);
        assertThat(after.path("items").isEmpty()).isTrue();
    }

    @Test
    void summariesNeverLoadSceneEntitiesOrTransferDocumentsAndCompiledArtifacts() throws Exception {
        long sceneId = scene("Lightweight", "2026-01-01T00:00:00Z");
        tag(sceneId, "fire");
        JsonNode before = request(get(PATH));
        String largeDocument = this.json.writeValueAsString(Map.of("schemaVersion", 1, "kind", "template",
                "templateId", "embedded-scene-0", "templateVersion", 1,
                "shader", "EXECUTABLE_CONTENT_MUST_STAY_PRIVATE".repeat(5000),
                "compiledArtifact", "COMPILED_CONTENT_MUST_STAY_PRIVATE".repeat(5000)));
        this.jdbc.update("UPDATE scenes SET scene_data = ?::jsonb WHERE id = ?", largeDocument, sceneId);
        this.entityManager.clear();
        Statistics statistics = this.entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        JsonNode after = request(get(PATH));

        assertThat(after).isEqualTo(before);
        assertThat(statistics.getEntityLoadCount()).as("Summary requests must not hydrate Scene or User entities").isZero();
        assertThat(after.toString()).doesNotContain("sceneData", "scene_data", "shader", "compiledArtifact",
                "EXECUTABLE_CONTENT", "COMPILED_CONTENT", "password", "email", "token");
        assertThat(after.path("items").get(0).path("creatorHandle").asText()).isEqualTo(this.owner.getHandle());
        assertThat(after.path("items").get(0).path("engagement").path("comments").asLong()).isZero();
        assertThat(after.path("items").get(0).path("engagement").path("likesRatio").asInt()).isZero();
        assertThat(after.path("items").get(0).path("availability").path("available").asBoolean()).isTrue();
        assertThat(this.jdbc.queryForObject("SELECT COUNT(*) FROM pg_indexes WHERE indexname = 'scenes_created_at_id_idx'",
                Long.class)).isEqualTo(1L);
    }

    @Test
    void publicAndAuthenticatedDiscoveryPreserveViewerStateAndPrivateOwnerRoutes() throws Exception {
        long sceneId = scene("Voted", "2026-01-01T00:00:00Z");
        vote(sceneId, this.owner.getId(), 1);
        this.jdbc.update("INSERT INTO scene_saves (scene_id, user_id) VALUES (?, ?)", sceneId, this.owner.getId());
        long commentId = this.jdbc.queryForObject("INSERT INTO scene_comments (scene_id,user_id,body) VALUES (?,?,'Parent') RETURNING id",
                Long.class, sceneId, this.owner.getId());
        this.jdbc.update("INSERT INTO scene_comments (scene_id,user_id,parent_comment_id,body) VALUES (?,?,?,'Reply')",
                sceneId, this.owner.getId(), commentId);
        String ownerToken = this.tokens.issueToken(this.owner);
        String otherToken = this.tokens.issueToken(user("viewer"));
        JsonNode anonymous = request(get(PATH)).path("items").get(0).path("engagement");
        assertThat(anonymous.path("currentUserVote").isNull()).isTrue();
        assertThat(anonymous.path("currentUserSaved").asBoolean()).isFalse();
        assertThat(anonymous.path("comments").asLong()).isEqualTo(2);
        assertThat(anonymous.path("likesRatio").asInt()).isEqualTo(100);
        JsonNode signedIn = request(get(PATH).header("Authorization", "Bearer " + ownerToken)).path("items").get(0).path("engagement");
        assertThat(signedIn.path("currentUserVote").asText()).isEqualTo("up");
        assertThat(signedIn.path("currentUserSaved").asBoolean()).isTrue();
        this.mvc.perform(get(PATH).header("Authorization", "Bearer invalid-token")).andExpect(status().isUnauthorized());
        this.mvc.perform(get("/api/users/{id}/scenes", this.owner.getId())).andExpect(status().isUnauthorized());
        this.mvc.perform(get("/api/users/{id}/scenes", this.owner.getId()).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
        this.mvc.perform(get("/api/scenes/{id}/repair", sceneId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());

        // Existing full-list and detail contracts are unchanged for their other consumers.
        this.mvc.perform(get("/api/scenes")).andExpect(status().isOk()).andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].sceneData.templateId").value("embedded-scene-0"));
        this.mvc.perform(get("/api/scenes/{id}", sceneId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.sceneData.templateId").value("embedded-scene-0"));
    }

    @Test
    void unavailableScenesKeepTheirMetadataAndFreshStatusWithoutSourceOrModeratorDetails() throws Exception {
        long blocked = scene("Blocked", "2026-01-01T00:00:00Z");
        long custom = scene("Custom disabled", "2026-02-01T00:00:00Z");
        long legacy = scene("Historical", "2026-03-01T00:00:00Z");
        this.jdbc.update("""
                INSERT INTO scene_availability_controls (scene_id,disabled,changed_at,reason)
                VALUES (?,TRUE,CURRENT_TIMESTAMP,'PRIVATE_MODERATOR_REASON')
                """, blocked);
        this.jdbc.update("UPDATE scenes SET scene_data = ?::jsonb, scene_mode = 'custom-v1' WHERE id = ?",
                "{\"schemaVersion\":1,\"kind\":\"custom\",\"scene\":{\"visualizer\":{\"shader\":\"sphere(0.5)\"}}}", custom);
        this.jdbc.update("UPDATE scenes SET scene_mode = 'legacy-custom' WHERE id = ?", legacy);
        JsonNode result = request(get(PATH).param("sort", "ascending"));
        assertThat(ids(result)).containsExactly(blocked, custom, legacy);
        assertThat(result.path("items").get(0).path("availability").path("code").asText()).isEqualTo("SCENE_DISABLED");
        assertThat(result.path("items").get(1).path("availability").path("code").asText()).isEqualTo("CUSTOM_RENDERING_DISABLED");
        assertThat(result.path("items").get(2).path("availability").path("code").asText()).isEqualTo("SCENE_UPGRADE_REQUIRED");
        assertThat(result.toString()).doesNotContain("PRIVATE_MODERATOR_REASON", "changedBy", "sphere(0.5)", "sceneData");
        this.mvc.perform(get("/api/scenes/{id}", blocked)).andExpect(status().isOk())
                .andExpect(jsonPath("$.sceneData").doesNotExist());
        this.jdbc.update("UPDATE scene_availability_controls SET disabled = FALSE WHERE scene_id = ?", blocked);
        assertThat(request(get(PATH).param("sort", "ascending")).path("items").get(0)
                .path("availability").path("available").asBoolean()).isTrue();
    }

    private JsonNode request(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = this.mvc.perform(request).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andReturn();
        return this.json.readTree(result.getResponse().getContentAsString());
    }

    private static void assertPage(JsonNode response, int page, int size, long total, long pages) {
        assertThat(response.path("page").asInt()).isEqualTo(page);
        assertThat(response.path("size").asInt()).isEqualTo(size);
        assertThat(response.path("totalElements").asLong()).isEqualTo(total);
        assertThat(response.path("totalPages").asLong()).isEqualTo(pages);
    }

    private static List<Long> ids(JsonNode response) {
        List<Long> result = new ArrayList<>();
        for (JsonNode scene : response.path("items")) result.add(scene.path("sceneId").asLong());
        return result;
    }

    private User user(String prefix) {
        String handle = prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return this.users.saveAndFlush(new User(handle + "@example.com", "unused-test-hash", prefix, "", prefix, handle));
    }

    private long scene(String name, String timestamp) {
        return this.jdbc.queryForObject("""
                INSERT INTO scenes (owner_user_id,name,description,scene_data,scene_mode,thumbnail_ref,created_at)
                VALUES (?,?,'A discovery fixture',?::jsonb,'template-v1','https://cdn.example.com/image.png',?) RETURNING id
                """, Long.class, this.owner.getId(), name, DOCUMENT, java.sql.Timestamp.from(Instant.parse(timestamp)));
    }

    private void tag(long sceneId, String name) {
        long tagId = this.jdbc.queryForObject("INSERT INTO tags (name) VALUES (?) ON CONFLICT (name) DO UPDATE SET name = EXCLUDED.name RETURNING id",
                Long.class, name);
        this.jdbc.update("INSERT INTO scene_tags (scene_id,tag_id) VALUES (?,?)", sceneId, tagId);
    }

    private void vote(long sceneId, long userId, int value) {
        this.jdbc.update("INSERT INTO scene_votes (scene_id,user_id,vote_value) VALUES (?,?,?)", sceneId, userId, value);
    }

    private void view(long sceneId) { this.jdbc.update("INSERT INTO scene_views (scene_id) VALUES (?)", sceneId); }
}
