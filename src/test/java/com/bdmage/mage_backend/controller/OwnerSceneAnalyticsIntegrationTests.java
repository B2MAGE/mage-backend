package com.bdmage.mage_backend.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.bdmage.mage_backend.model.SceneComment;
import com.bdmage.mage_backend.repository.SceneCommentRepository;
import com.bdmage.mage_backend.support.PostgresIntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real authentication and PostgreSQL aggregates, isolated from the development database. */
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.stat=OFF",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"
})
@AutoConfigureMockMvc
@Testcontainers
class OwnerSceneAnalyticsIntegrationTests extends PostgresIntegrationTestSupport {

    private final ObjectMapper json = new ObjectMapper();

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SceneCommentRepository comments;
    @Autowired private EntityManagerFactory entityManagerFactory;

    @Test
    void ownerLibraryRequiresOwnerAuthenticationWhilePublicProfileRemainsPublic() throws Exception {
        Account owner = registerAndLogin();
        Account other = registerAndLogin();
        long sceneId = createScene(owner, "Public design");

        this.mvc.perform(get("/api/users/{id}/scenes", owner.id()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        this.mvc.perform(get("/api/users/{id}/scenes", owner.id())
                        .header("Authorization", "Bearer " + other.token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SCENE_OWNERSHIP_REQUIRED"));

        JsonNode library = ownerLibrary(owner);
        assertThat(library.size()).isEqualTo(1);
        assertThat(library.get(0).path("sceneId").asLong()).isEqualTo(sceneId);
        this.mvc.perform(get("/api/profiles/{handle}", owner.handle()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(owner.id()))
                .andExpect(jsonPath("$.scenes[0].sceneId").value(sceneId))
                .andExpect(jsonPath("$.scenes[0].availability.available").value(true));
    }

    @Test
    void aggregatesPersistedEngagementAndRepliesWithoutMultiplyingOrMixingScenes() throws Exception {
        Account owner = registerAndLogin();
        Account secondVoter = registerAndLogin();
        Account otherOwner = registerAndLogin();
        long active = createScene(owner, "Active design");
        long quiet = createScene(owner, "Quiet design");
        long unrelated = createScene(otherOwner, "Another creator's design");

        view(active, null);
        view(active, owner.id());
        view(active, secondVoter.id());
        vote(active, owner.id(), 1);
        vote(active, secondVoter.id(), 1);
        vote(active, otherOwner.id(), -1);
        save(active, owner.id());
        save(active, secondVoter.id());
        SceneComment parent = comment(active, otherOwner.id(), null);
        comment(active, owner.id(), parent.getId());
        comment(active, secondVoter.id(), null);

        // Rows for an unrequested scene must not leak into the owner's totals.
        view(unrelated, null);
        vote(unrelated, owner.id(), -1);
        save(unrelated, owner.id());
        comment(unrelated, owner.id(), null);

        JsonNode library = ownerLibrary(owner);
        assertThat(library.size()).isEqualTo(2);
        JsonNode actual = scene(library, active);
        assertThat(actual.path("name").asText()).isEqualTo("Active design");
        assertThat(actual.path("ownerUserId").asLong()).isEqualTo(owner.id());
        assertThat(actual.path("availability").path("available").asBoolean()).isTrue();
        assertThat(actual.path("sceneMode").asText()).isEqualTo("template-v1");
        JsonNode engagement = actual.path("engagement");
        assertThat(engagement.path("views").asLong()).isEqualTo(3);
        assertThat(engagement.path("upvotes").asLong()).isEqualTo(2);
        assertThat(engagement.path("downvotes").asLong()).isEqualTo(1);
        assertThat(engagement.path("saves").asLong()).isEqualTo(2);
        assertThat(engagement.path("comments").asLong()).isEqualTo(3);
        assertThat(engagement.path("likesRatio").isIntegralNumber()).isTrue();
        assertThat(engagement.path("likesRatio").asInt()).isEqualTo(67);
        assertThat(engagement.path("currentUserVote").asText()).isEqualTo("up");
        assertThat(engagement.path("currentUserSaved").asBoolean()).isTrue();
        assertZeroEngagement(scene(library, quiet).path("engagement"));

        // Detail and list responses use the same aggregation contract.
        JsonNode detail = body(this.mvc.perform(get("/api/scenes/{id}", active)
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk()).andReturn());
        assertThat(detail.path("engagement")).isEqualTo(engagement);
    }

    @Test
    void emptyLibraryAndSceneWithoutEngagementReturnExplicitZeroMetrics() throws Exception {
        Account owner = registerAndLogin();
        assertThat(ownerLibrary(owner).isEmpty()).isTrue();

        long sceneId = createScene(owner, "Just created");
        assertZeroEngagement(scene(ownerLibrary(owner), sceneId).path("engagement"));
    }

    @Test
    void blockedSceneKeepsOwnerAnalyticsWhileItsDocumentIsSuppressed() throws Exception {
        Account owner = registerAndLogin();
        long blocked = createScene(owner, "Temporarily unavailable");
        createScene(owner, "Still available");
        view(blocked, null);
        vote(blocked, owner.id(), 1);
        save(blocked, owner.id());
        SceneComment parent = comment(blocked, owner.id(), null);
        comment(blocked, owner.id(), parent.getId());
        JsonNode before = scene(ownerLibrary(owner), blocked).path("engagement");
        assertThat(before.path("comments").asLong()).isEqualTo(2);
        assertThat(before.path("views").asLong()).isEqualTo(1);

        this.jdbc.update("""
                INSERT INTO scene_availability_controls (scene_id, disabled, changed_at, reason)
                VALUES (?, TRUE, CURRENT_TIMESTAMP, 'Integration test playback block')
                ON CONFLICT (scene_id) DO UPDATE SET disabled = TRUE,
                    changed_at = CURRENT_TIMESTAMP, reason = EXCLUDED.reason
                """, blocked);

        JsonNode library = ownerLibrary(owner);
        assertThat(library.size()).isEqualTo(2);
        JsonNode actual = scene(library, blocked);
        assertThat(actual.path("sceneData").isNull() || actual.path("sceneData").isMissingNode()).isTrue();
        assertThat(actual.path("availability").path("available").asBoolean()).isFalse();
        assertThat(actual.path("availability").path("code").asText()).isEqualTo("SCENE_DISABLED");
        assertThat(actual.path("engagement")).isEqualTo(before);
    }

    @Test
    void anonymousCollectionsRetainTotalsWithoutInheritingAVotersPersonalState() throws Exception {
        Account owner = registerAndLogin();
        long sceneId = createScene(owner, "Public engagement");
        view(sceneId, owner.id());
        vote(sceneId, owner.id(), 1);
        save(sceneId, owner.id());
        comment(sceneId, owner.id(), null);
        assertThat(scene(ownerLibrary(owner), sceneId).path("engagement").path("currentUserSaved").asBoolean())
                .isTrue();

        JsonNode publicScenes = body(this.mvc.perform(get("/api/scenes"))
                .andExpect(status().isOk()).andReturn());
        JsonNode publicProfile = body(this.mvc.perform(get("/api/profiles/{handle}", owner.handle()))
                .andExpect(status().isOk()).andReturn());
        for (JsonNode collection : List.of(publicScenes, publicProfile.path("scenes"))) {
            JsonNode engagement = scene(collection, sceneId).path("engagement");
            assertThat(engagement.path("views").asLong()).isEqualTo(1);
            assertThat(engagement.path("upvotes").asLong()).isEqualTo(1);
            assertThat(engagement.path("saves").asLong()).isEqualTo(1);
            assertThat(engagement.path("comments").asLong()).isEqualTo(1);
            assertThat(engagement.path("likesRatio").asInt()).isEqualTo(100);
            assertThat(engagement.path("currentUserVote").isNull()
                    || engagement.path("currentUserVote").isMissingNode()).isTrue();
            assertThat(engagement.path("currentUserSaved").isBoolean()).isTrue();
            assertThat(engagement.path("currentUserSaved").asBoolean()).isFalse();
        }
    }

    @Test
    void commentTotalsReflectDeletedRepliesAndCascadedParentDeletion() throws Exception {
        Account owner = registerAndLogin();
        long sceneId = createScene(owner, "Discussion");
        SceneComment parent = comment(sceneId, owner.id(), null);
        SceneComment firstReply = comment(sceneId, owner.id(), parent.getId());
        comment(sceneId, owner.id(), parent.getId());
        assertThat(scene(ownerLibrary(owner), sceneId).path("engagement").path("comments").asLong())
                .isEqualTo(3);

        // There is no comment-deletion HTTP route; delete only these disposable test rows.
        this.comments.deleteById(firstReply.getId());
        assertThat(scene(ownerLibrary(owner), sceneId).path("engagement").path("comments").asLong())
                .isEqualTo(2);
        this.comments.deleteById(parent.getId());
        assertThat(scene(ownerLibrary(owner), sceneId).path("engagement").path("comments").asLong())
                .isZero();
        assertThat(this.jdbc.queryForObject("SELECT count(*) FROM scene_comments WHERE scene_id = ?",
                Long.class, sceneId)).isZero();
    }

    @Test
    void voteRatiosRoundToWholePercentAndHandleBothExtremes() throws Exception {
        Account owner = registerAndLogin();
        Account second = registerAndLogin();
        Account third = registerAndLogin();
        long mixed = createScene(owner, "One of three positive");
        long positive = createScene(owner, "Positive");
        long negative = createScene(owner, "Negative");
        vote(mixed, owner.id(), 1);
        vote(mixed, second.id(), -1);
        vote(mixed, third.id(), -1);
        vote(positive, owner.id(), 1);
        vote(negative, owner.id(), -1);

        JsonNode library = ownerLibrary(owner);
        assertThat(scene(library, mixed).path("engagement").path("likesRatio").asInt()).isEqualTo(33);
        assertThat(scene(library, positive).path("engagement").path("likesRatio").asInt()).isEqualTo(100);
        assertThat(scene(library, negative).path("engagement").path("likesRatio").asInt()).isZero();
        assertThat(scene(library, negative).path("engagement").path("currentUserVote").asText())
                .isEqualTo("down");
    }

    @Test
    void libraryDatabaseStatementCountStaysConstantAsSceneCountGrows() throws Exception {
        Account owner = registerAndLogin();
        seedEngagedScene(owner, "First design");
        // Exclude login, fixture writes and first-request initialization; count normal request authentication.
        ownerLibrary(owner);
        Statistics statistics = this.entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        assertThat(ownerLibrary(owner).size()).isEqualTo(1);
        long oneSceneStatements = statistics.getPrepareStatementCount();

        for (int index = 1; index < 12; index++) {
            seedEngagedScene(owner, "Design " + index);
        }
        statistics.clear();
        JsonNode library = ownerLibrary(owner);
        long twelveSceneStatements = statistics.getPrepareStatementCount();
        assertThat(library.size()).isEqualTo(12);
        assertThat(oneSceneStatements).isPositive();
        assertThat(twelveSceneStatements)
                .as("owner, tags, availability and engagement must be batched, not queried per scene")
                .isEqualTo(oneSceneStatements);
        for (JsonNode scene : library) {
            assertThat(scene.path("engagement").path("comments").asLong()).isEqualTo(1);
            assertThat(scene.path("engagement").path("views").asLong()).isEqualTo(1);
            assertThat(scene.path("engagement").path("likesRatio").asInt()).isEqualTo(100);
        }
    }

    private void seedEngagedScene(Account owner, String name) throws Exception {
        long sceneId = createScene(owner, name);
        view(sceneId, null);
        vote(sceneId, owner.id(), 1);
        save(sceneId, owner.id());
        comment(sceneId, owner.id(), null);
    }

    private JsonNode ownerLibrary(Account owner) throws Exception {
        return body(this.mvc.perform(get("/api/users/{id}/scenes", owner.id())
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isArray()).andReturn());
    }

    private JsonNode scene(JsonNode library, long sceneId) {
        for (JsonNode scene : library) {
            if (scene.path("sceneId").asLong() == sceneId) return scene;
        }
        throw new AssertionError("Scene " + sceneId + " missing from owner library");
    }

    private void assertZeroEngagement(JsonNode engagement) {
        for (String metric : List.of("views", "upvotes", "downvotes", "saves", "comments", "likesRatio")) {
            assertThat(engagement.path(metric).isIntegralNumber()).as(metric + " is present and integral").isTrue();
            assertThat(engagement.path(metric).asLong()).as(metric).isZero();
        }
        assertThat(engagement.path("currentUserVote").isMissingNode()
                || engagement.path("currentUserVote").isNull()).isTrue();
        assertThat(engagement.path("currentUserSaved").isBoolean()).isTrue();
        assertThat(engagement.path("currentUserSaved").asBoolean()).isFalse();
    }

    private long createScene(Account owner, String name) throws Exception {
        Map<String, Object> request = Map.of("name", name, "description", "Analytics fixture", "tagIds", List.of(),
                "sceneData", Map.of("schemaVersion", 1, "kind", "template", "templateId", "embedded-scene-0",
                        "templateVersion", 1));
        return body(this.mvc.perform(post("/api/scenes").header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON).content(this.json.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.availability.available").value(true)).andReturn()).path("sceneId").asLong();
    }

    private Account registerAndLogin() throws Exception {
        String handle = "analytics" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String email = handle + "@example.com";
        String password = "analytics-test-password";
        this.mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(this.json.writeValueAsString(Map.of("email", email, "password", password,
                                "firstName", "Analytics", "lastName", "User", "displayName", "Analytics User",
                                "handle", "@" + handle))))
                .andExpect(status().isCreated());
        JsonNode login = body(this.mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(this.json.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty()).andReturn());
        return new Account(login.path("userId").asLong(), handle, login.path("accessToken").asText());
    }

    private JsonNode body(MvcResult result) throws Exception {
        return this.json.readTree(result.getResponse().getContentAsString());
    }

    private void view(long sceneId, Long userId) {
        this.jdbc.update("INSERT INTO scene_views (scene_id, user_id) VALUES (?, ?)", sceneId, userId);
    }

    private void vote(long sceneId, long userId, int value) {
        this.jdbc.update("INSERT INTO scene_votes (scene_id, user_id, vote_value) VALUES (?, ?, ?)", sceneId, userId, value);
    }

    private void save(long sceneId, long userId) {
        this.jdbc.update("INSERT INTO scene_saves (scene_id, user_id) VALUES (?, ?)", sceneId, userId);
    }

    private SceneComment comment(long sceneId, long userId, Long parentId) {
        return this.comments.saveAndFlush(new SceneComment(sceneId, userId, parentId, "A persisted comment."));
    }

    private record Account(long id, String handle, String token) {}
}
