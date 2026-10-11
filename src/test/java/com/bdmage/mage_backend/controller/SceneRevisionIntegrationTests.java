package com.bdmage.mage_backend.controller;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.bdmage.mage_backend.repository.SceneRepository;
import com.bdmage.mage_backend.service.ThumbnailStorageService;
import com.bdmage.mage_backend.support.PostgresIntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Real HTTP/authentication and disposable PostgreSQL; no existing local accounts or scenes. */
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SceneRevisionIntegrationTests extends PostgresIntegrationTestSupport {
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    @LocalServerPort private int port;
    @Autowired private SceneRepository scenes;
    @MockitoBean private ThumbnailStorageService thumbnails;
    private String token;

    @BeforeEach
    void registerDisposableAccount() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String password = "SB08!" + UUID.randomUUID();
        ObjectNode account = this.json.createObjectNode().put("email", "sb08-" + suffix + "@example.test")
                .put("password", password).put("firstName", "Builder").put("lastName", "Test")
                .put("displayName", "Builder test").put("handle", "@sb08_" + suffix);
        send("POST", "/api/auth/register", account, 201);
        this.token = send("POST", "/api/auth/login", this.json.createObjectNode()
                .put("email", account.path("email").asText()).put("password", password), 200).path("accessToken").asText();
    }

    @Test
    void builderObjectsBindingsAndOrderedStagesSurviveSaveReopenAndExportImport() throws Exception {
        JsonNode created = create();
        long id = created.path("sceneId").asLong();
        assertThat(created.path("revision").asLong()).isZero();
        ObjectNode edited = (ObjectNode) created.path("sceneData").deepCopy();
        var objects = edited.withArray("objects");
        JsonNode first = objects.remove(0);
        objects.add(first); // reorder retains identity and the nested property bindings
        ObjectNode duplicate = (ObjectNode) objects.get(0).deepCopy();
        duplicate.put("id", "plain-copy").put("name", "Duplicate");
        objects.add(duplicate);
        ((ObjectNode) first.path("motion")).put("speed", -0.75);
        JsonNode updated = send("PUT", "/api/scenes/" + id, request(edited).put("expectedRevision", 0), 200);
        assertThat(updated.path("revision").asLong()).isEqualTo(1);
        assertThat(updated.path("sceneData")).isEqualTo(edited);
        JsonNode reopened = send("GET", "/api/scenes/" + id, null, 200);
        assertThat(reopened.path("sceneData")).isEqualTo(edited);
        assertThat(reopened.path("sceneMode").asText()).isEqualTo("builder-v1");
        JsonNode exported = send("GET", "/api/scenes/" + id + "/repair", null, 200);
        assertThat(exported.path("revision").asLong()).isEqualTo(1);
        assertThat(exported.path("sceneData")).isEqualTo(edited);
        JsonNode imported = send("POST", "/api/scenes", request(this.json.readTree(exported.path("sceneData").toString())), 201);
        assertThat(imported.path("sceneData")).isEqualTo(edited);
        assertThat(imported.path("revision").asLong()).isZero();
        assertThat(this.scenes.findById(id).orElseThrow().getSceneData()).isEqualTo(edited);

        ObjectNode excessive = edited.deepCopy();
        ((ObjectNode) excessive.path("objects").get(1).path("arrangements").get(0)).put("count", 8);
        send("PUT", "/api/scenes/" + id, request(excessive).put("expectedRevision", 1), 400);
        assertThat(send("GET", "/api/scenes/" + id, null, 200).path("revision").asLong()).isEqualTo(1);
        assertThat(this.scenes.findById(id).orElseThrow().getSceneData()).isEqualTo(edited);
    }

    @Test
    void staleSaveCannotReplaceNewerDocumentAndCanRetryAfterExplicitReopen() throws Exception {
        JsonNode created = create();
        long id = created.path("sceneId").asLong();
        ObjectNode first = request(created.path("sceneData")).put("expectedRevision", 0).put("name", "Newer saved scene");
        send("PUT", "/api/scenes/" + id, first, 200);
        ObjectNode stale = request(created.path("sceneData")).put("expectedRevision", 0).put("name", "Old tab");
        JsonNode conflict = send("PUT", "/api/scenes/" + id, stale, 409);
        assertThat(conflict.path("code").asText()).isEqualTo("SCENE_EDIT_CONFLICT");
        assertThat(conflict.path("message").asText()).contains("draft", "reopen");
        JsonNode reopened = send("GET", "/api/scenes/" + id, null, 200);
        assertThat(reopened.path("name").asText()).isEqualTo("Newer saved scene");
        send("PUT", "/api/scenes/" + id, stale.put("expectedRevision", reopened.path("revision").asLong()), 200);
        assertThat(this.scenes.findById(id).orElseThrow().getRevision()).isEqualTo(2);
    }

    @Test
    void concurrentSavesOfTheSameRevisionHaveExactlyOneWinner() throws Exception {
        JsonNode created = create();
        long id = created.path("sceneId").asLong();
        var pending = new ArrayList<CompletableFuture<HttpResponse<String>>>();
        for (int index = 0; index < 8; index++) {
            pending.add(this.client.sendAsync(httpRequest("PUT", "/api/scenes/" + id,
                    request(created.path("sceneData")).put("expectedRevision", 0).put("name", "Writer " + index)),
                    HttpResponse.BodyHandlers.ofString()));
        }
        var statuses = pending.stream().map(CompletableFuture::join).map(HttpResponse::statusCode).toList();
        assertThat(statuses.stream().filter(status -> status == 200).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(status -> status == 409).count()).isEqualTo(7);
        assertThat(this.scenes.findById(id).orElseThrow().getRevision()).isEqualTo(1);
    }

    @Test
    void descriptionAndEveryTagWriteInvalidateAnOlderFullSave() throws Exception {
        JsonNode created = create();
        long id = created.path("sceneId").asLong();
        String path = "/api/scenes/" + id;
        ObjectNode stale = request(created.path("sceneData")).put("expectedRevision", 0);
        send("PATCH", path + "/description", this.json.createObjectNode().put("description", "Latest description"), 200);
        send("PUT", path, stale, 409);
        JsonNode tag = send("POST", "/api/tags", this.json.createObjectNode().put("name", "sb08-" + UUID.randomUUID()), 201);
        long tagId = tag.path("tagId").asLong();
        send("POST", path + "/tags", this.json.createObjectNode().put("tagId", tagId), 201);
        assertThat(send("GET", path, null, 200).path("revision").asLong()).isEqualTo(2);
        send("PUT", path, stale.put("expectedRevision", 1), 409);
        send("PUT", path + "/tags", this.json.createObjectNode().set("tagIds", this.json.createArrayNode().add(tagId)), 200);
        send("PUT", path, stale.put("expectedRevision", 2), 409);
        send("DELETE", path + "/tags/" + tagId, null, 204);
        send("PUT", path, stale.put("expectedRevision", 3), 409);
        JsonNode latest = send("GET", path, null, 200);
        assertThat(latest.path("revision").asLong()).isEqualTo(4);
        assertThat(latest.path("description").asText()).isEqualTo("Latest description");
        assertThat(latest.path("tags")).isEmpty();
    }

    @Test
    void thumbnailAndEngagementUpdatesDoNotInvalidateTheEditorRevision() throws Exception {
        JsonNode created = create();
        long id = created.path("sceneId").asLong();
        when(this.thumbnails.finalizeUpload(id, "test-thumbnail")).thenReturn(
                new ThumbnailStorageService.FinalizedThumbnail("test-thumbnail", "https://cdn.test.example.com/current.png"));
        send("POST", "/api/scenes/" + id + "/thumbnail/finalize", this.json.createObjectNode().put("objectKey", "test-thumbnail"), 200);
        send("POST", "/api/scenes/" + id + "/views", null, 200);
        assertThat(send("GET", "/api/scenes/" + id, null, 200).path("revision").asLong()).isZero();
        JsonNode updated = send("PUT", "/api/scenes/" + id, request(created.path("sceneData")).put("expectedRevision", 0), 200);
        assertThat(updated.path("revision").asLong()).isEqualTo(1);
        assertThat(updated.path("thumbnailRef").asText()).isEqualTo("https://cdn.test.example.com/current.png");
    }

    @Test
    void missingOrInvalidRevisionIsRejectedWithoutMutation() throws Exception {
        JsonNode created = create();
        long id = created.path("sceneId").asLong();
        ObjectNode replacement = request(created.path("sceneData")).put("name", "Must not save");
        assertThat(send("PUT", "/api/scenes/" + id, replacement, 400).path("details").has("expectedRevision")).isTrue();
        send("PUT", "/api/scenes/" + id, replacement.put("expectedRevision", -1), 400);
        send("PUT", "/api/scenes/" + id, replacement.put("expectedRevision", 0.5), 400);
        send("PUT", "/api/scenes/" + id, replacement.put("expectedRevision", "0"), 400);
        assertThat(this.scenes.findById(id).orElseThrow().getRevision()).isZero();
    }

    private JsonNode create() throws Exception {
        return send("POST", "/api/scenes", request(this.json.readTree("""
                {"schemaVersion":1,"kind":"builder","builderVersion":1,"objects":[
                  {"id":"stable-arranged","name":"Arranged box","operation":{"type":"box"},
                   "modifiers":[{"type":"expand","amount":0.2},{"type":"shell","thickness":0.1},{"type":"twist","axis":"y","amount":1.5}],
                   "arrangements":[{"type":"linear","axis":"x","count":2,"spacing":1},{"type":"radial","axis":"z","count":3,"radius":2}],
                   "motion":{"type":"spin","axis":"y","speed":0.5},
                   "bindings":[{"target":"scale.x","source":"bass-hit","amount":0.7,"attack":0,"release":0}]},
                  {"id":"stable-plain","name":"Plain sphere","operation":{"type":"sphere","radius":0.5}}]}
                """)), 201);
    }

    private ObjectNode request(JsonNode document) {
        return this.json.createObjectNode().put("name", "Builder round trip")
                .<ObjectNode>set("tagIds", this.json.createArrayNode()).set("sceneData", document);
    }

    private HttpRequest httpRequest(String method, String path, JsonNode body) {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + this.port + path))
                .timeout(Duration.ofSeconds(30)).header("Content-Type", "application/json");
        if (this.token != null) request.header("Authorization", "Bearer " + this.token);
        return request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body.toString())).build();
    }

    private JsonNode send(String method, String path, JsonNode body, int expectedStatus) throws Exception {
        HttpResponse<String> response = this.client.send(httpRequest(method, path, body), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(method + " " + path).isEqualTo(expectedStatus);
        return response.body().isBlank() ? this.json.nullNode() : this.json.readTree(response.body());
    }
}
