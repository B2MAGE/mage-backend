package com.bdmage.mage_backend.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.bdmage.mage_backend.model.Playlist;
import com.bdmage.mage_backend.model.Scene;
import com.bdmage.mage_backend.model.SceneTag;
import com.bdmage.mage_backend.model.Tag;
import com.bdmage.mage_backend.repository.PlaylistRepository;
import com.bdmage.mage_backend.repository.SceneRepository;
import com.bdmage.mage_backend.repository.SceneTagRepository;
import com.bdmage.mage_backend.repository.TagRepository;
import com.bdmage.mage_backend.support.PostgresIntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real HTTP authentication, real PostgreSQL transactions, no ambient test transaction. */
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AtomicSceneTagsIntegrationTests extends PostgresIntegrationTestSupport {
    private final ObjectMapper json = new ObjectMapper();
    @Autowired private MockMvc mvc;
    @Autowired private SceneRepository scenes;
    @Autowired private SceneTagRepository sceneTags;
    @Autowired private TagRepository tags;
    @Autowired private PlaylistRepository playlists;
    @Autowired private JdbcTemplate jdbc;
    private Account owner;
    private Tag first;
    private Tag second;

    @BeforeEach
    void setUp() throws Exception {
        this.owner = registerAndLogin();
        this.first = this.tags.saveAndFlush(new Tag("first-" + UUID.randomUUID()));
        this.second = this.tags.saveAndFlush(new Tag("second-" + UUID.randomUUID()));
    }

    @Test
    void createReplaceAndClearPersistSceneAndExactSelectionTogether() throws Exception {
        long id = create(request("Created", List.of(this.first.getId(), this.first.getId(), this.second.getId())));
        assertTags(id, this.first.getId(), this.second.getId());
        ObjectNode replacement = request("Edited", List.of(this.second.getId())).put("description", "Edited description");
        ((ObjectNode) replacement.path("sceneData")).put("templateId", "embedded-scene-1");
        write(put("/api/scenes/{id}", id), replacement, this.owner.token())
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Edited"))
                .andExpect(jsonPath("$.tags[0]").value(this.second.getName()));
        Scene saved = this.scenes.findById(id).orElseThrow();
        assertThat(saved.getDescription()).isEqualTo("Edited description");
        assertThat(saved.getSceneData().path("templateId").asText()).isEqualTo("embedded-scene-1");
        assertTags(id, this.second.getId());
        write(put("/api/scenes/{id}", id), request("Cleared", List.of()), this.owner.token())
                .andExpect(status().isOk()).andExpect(jsonPath("$.tags").isEmpty());
        assertTags(id);
        assertThat(this.scenes.findById(id).orElseThrow().getName()).isEqualTo("Cleared");
    }

    @Test
    void unknownTagsOrInvalidSceneLeaveCreateAndUpdateUnchanged() throws Exception {
        long id = create(request("Original", List.of(this.first.getId())));
        Scene original = this.scenes.findById(id).orElseThrow();
        long count = this.scenes.count();
        ObjectNode missingTag = request("Must not persist", List.of(this.second.getId(), Long.MAX_VALUE));
        for (MockHttpServletRequestBuilder endpoint : List.of(post("/api/scenes"), put("/api/scenes/{id}", id))) {
            write(endpoint, missingTag, this.owner.token()).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("TAG_NOT_FOUND"));
        }
        ObjectNode invalidScene = request("Invalid document", List.of(this.second.getId()));
        invalidScene.set("sceneData", this.json.createObjectNode());
        for (MockHttpServletRequestBuilder endpoint : List.of(post("/api/scenes"), put("/api/scenes/{id}", id))) {
            write(endpoint, invalidScene, this.owner.token()).andExpect(status().isBadRequest());
        }
        assertThat(this.scenes.count()).isEqualTo(count);
        assertSceneUnchanged(original);
        assertTags(id, this.first.getId());
    }

    @Test
    void tagSelectionIsRequiredAndNullEntriesAreRejected() throws Exception {
        long id = create(request("Original", List.of(this.first.getId())));
        for (String selection : List.of("missing", "null", "null-entry")) {
            ObjectNode body = request("Invalid selection", List.of());
            if (selection.equals("missing")) body.remove("tagIds");
            else if (selection.equals("null")) body.putNull("tagIds");
            else body.putArray("tagIds").addNull();
            for (MockHttpServletRequestBuilder endpoint : List.of(post("/api/scenes"), put("/api/scenes/{id}", id))) {
                write(endpoint, body, this.owner.token()).andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
            }
        }
        assertTags(id, this.first.getId());
        assertThat(this.scenes.findById(id).orElseThrow().getName()).isEqualTo("Original");
    }

    @Test
    void lateDatabaseFailureRollsBackSceneTagsAndPlaylistAttachment() throws Exception {
        long id = create(request("Original", List.of(this.first.getId())));
        Scene original = this.scenes.findById(id).orElseThrow();
        long sceneCount = this.scenes.count();
        long assignmentCount = this.sceneTags.count();
        Playlist playlist = this.playlists.saveAndFlush(new Playlist(this.owner.id(), "Atomic playlist"));
        ObjectNode body = request("Must roll back", List.of(this.first.getId(), this.second.getId()))
                .put("playlistId", playlist.getId());
        // Force a failure after the scene row is flushed and existing tags are removed.
        // The trigger exists only inside this disposable Testcontainers database.
        this.jdbc.execute("CREATE FUNCTION reject_atomic_test_tag() RETURNS trigger LANGUAGE plpgsql AS $$ "
                + "BEGIN RAISE EXCEPTION 'atomic tag failure'; END $$");
        this.jdbc.execute("CREATE TRIGGER reject_atomic_test_tag BEFORE INSERT ON scene_tags FOR EACH ROW "
                + "WHEN (NEW.tag_id = " + this.second.getId() + ") EXECUTE FUNCTION reject_atomic_test_tag()");
        try {
            for (MockHttpServletRequestBuilder endpoint : List.of(post("/api/scenes"), put("/api/scenes/{id}", id))) {
                assertThatThrownBy(() -> write(endpoint, body, this.owner.token()))
                        .hasStackTraceContaining("atomic tag failure");
                assertThat(this.scenes.count()).isEqualTo(sceneCount);
                assertThat(this.sceneTags.count()).isEqualTo(assignmentCount);
                assertSceneUnchanged(original);
                assertTags(id, this.first.getId());
                assertThat(this.jdbc.queryForObject("SELECT count(*) FROM scene_playlists WHERE playlist_id = ?",
                        Long.class, playlist.getId())).isZero();
                body.remove("playlistId"); // Only scene creation accepts a playlist attachment.
            }
        } finally {
            this.jdbc.execute("DROP TRIGGER reject_atomic_test_tag ON scene_tags");
            this.jdbc.execute("DROP FUNCTION reject_atomic_test_tag()");
        }
    }

    @Test
    void publicReadsStayPublicAndAllWritesRequireValidAuthentication() throws Exception {
        long id = create(request("Public", List.of(this.first.getId())));
        for (String path : List.of("/api/scenes", "/api/scenes/" + id, "/api/tags",
                "/api/tags?attachedOnly=true", "/api/profiles/" + this.owner.handle())) {
            this.mvc.perform(get(path)).andExpect(status().isOk());
        }
        for (String token : List.of("", "Bearer invalid", "Basic invalid", "Bearer ")) {
            List<MockHttpServletRequestBuilder> protectedRequests = List.of(
                    post("/api/tags").content("{\"name\":\"blocked-tag\"}"),
                    post("/api/scenes").content(request("Blocked", List.of()).toString()),
                    put("/api/scenes/{id}", id).content(request("Blocked", List.of()).toString()),
                    post("/api/scenes/{id}/tags", id).content("{\"tagId\":" + this.second.getId() + "}"),
                    put("/api/scenes/{id}/tags", id).content("{\"tagIds\":[]}"),
                    delete("/api/scenes/{id}/tags/{tagId}", id, this.first.getId()),
                    get("/api/users/me"), get("/api/users/{id}/scenes", this.owner.id()));
            for (MockHttpServletRequestBuilder endpoint : protectedRequests) {
                if (!token.isEmpty()) endpoint.header("Authorization", token);
                this.mvc.perform(endpoint.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
            }
        }
        assertThat(this.tags.findByName("blocked-tag")).isEmpty();
        assertTags(id, this.first.getId());
        this.mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + this.owner.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(this.owner.id()))
                .andExpect(jsonPath("$.handle").value(this.owner.handle()));
        write(post("/api/tags"), this.json.createObjectNode().put("name", "allowed-" + UUID.randomUUID()), this.owner.token())
                .andExpect(status().isCreated());
    }

    @Test
    void anotherAccountCannotChangeSceneOrItsTags() throws Exception {
        long id = create(request("Original", List.of(this.first.getId())));
        Scene original = this.scenes.findById(id).orElseThrow();
        Account other = registerAndLogin();
        for (MockHttpServletRequestBuilder endpoint : List.of(
                put("/api/scenes/{id}", id).content(request("Other account", List.of(this.second.getId())).toString()),
                post("/api/scenes/{id}/tags", id).content("{\"tagId\":" + this.second.getId() + "}"),
                put("/api/scenes/{id}/tags", id).content("{\"tagIds\":[]}"),
                delete("/api/scenes/{id}/tags/{tagId}", id, this.first.getId()))) {
            this.mvc.perform(endpoint.header("Authorization", "Bearer " + other.token()).contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("SCENE_OWNERSHIP_REQUIRED"));
        }
        assertSceneUnchanged(original);
        assertTags(id, this.first.getId());
    }

    private long create(ObjectNode body) throws Exception {
        return this.json.readTree(write(post("/api/scenes"), body, this.owner.token())
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("sceneId").asLong();
    }

    private ResultActions write(MockHttpServletRequestBuilder endpoint, JsonNode body, String token) throws Exception {
        return this.mvc.perform(endpoint.header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body.toString()));
    }

    private ObjectNode request(String name, List<Long> tagIds) {
        return this.json.valueToTree(Map.of("name", name, "description", "Original description", "tagIds", tagIds,
                "sceneData", Map.of("schemaVersion", 1, "kind", "template", "templateId", "embedded-scene-0", "templateVersion", 1)));
    }

    private void assertTags(long id, Long... expected) {
        assertThat(this.sceneTags.findAllBySceneId(id)).extracting(SceneTag::getTagId).containsExactlyInAnyOrder(expected);
    }

    private void assertSceneUnchanged(Scene original) {
        Scene actual = this.scenes.findById(original.getId()).orElseThrow();
        assertThat(actual.getName()).isEqualTo(original.getName());
        assertThat(actual.getDescription()).isEqualTo(original.getDescription());
        assertThat(actual.getSceneData()).isEqualTo(original.getSceneData());
        assertThat(actual.getSceneMode()).isEqualTo(original.getSceneMode());
        assertThat(actual.getThumbnailRef()).isEqualTo(original.getThumbnailRef());
    }

    private Account registerAndLogin() throws Exception {
        String handle = "atomic" + UUID.randomUUID().toString().replace("-", "").substring(0, 18);
        String email = handle + "@example.com";
        String password = "atomic-test-password";
        this.mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(this.json.writeValueAsString(Map.of("email", email, "password", password,
                        "firstName", "Atomic", "lastName", "User", "displayName", "Atomic User", "handle", "@" + handle))))
                .andExpect(status().isCreated());
        JsonNode login = this.json.readTree(this.mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(this.json.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return new Account(login.path("userId").asLong(), handle, login.path("accessToken").asText());
    }

    private record Account(long id, String handle, String token) {}
}
