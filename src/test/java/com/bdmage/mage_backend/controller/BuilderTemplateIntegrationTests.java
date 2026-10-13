package com.bdmage.mage_backend.controller;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import com.bdmage.mage_backend.service.BuilderTemplateAssetCleanup;
import com.bdmage.mage_backend.service.ThumbnailStorageService;
import com.bdmage.mage_backend.exception.ThumbnailStorageUnavailableException;
import com.bdmage.mage_backend.support.PostgresIntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Actual HTTP/login plus disposable PostgreSQL; storage failures are deterministic. */
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mage.builder-template.cleanup-delay-ms=3600000")
@Testcontainers
class BuilderTemplateIntegrationTests extends PostgresIntegrationTestSupport {
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private BuilderTemplateAssetCleanup cleanup;
    @MockitoBean private ThumbnailStorageService thumbnails;
    private String token;
    private String otherToken;

    @BeforeEach
    void setup() throws Exception {
        this.token = null;
        this.otherToken = register();
        this.token = register();
        when(this.thumbnails.createTemplateUpload(anyLong(), anyString(), anyString())).thenAnswer(invocation -> {
            String key = "scenes/builder-templates/pending/" + invocation.getArgument(0) + "/" + UUID.randomUUID() + ".png";
            return new ThumbnailStorageService.PresignedThumbnailUpload(key, "https://upload.example.test/" + key,
                    "PUT", Map.of("Content-Type", "image/png"), Instant.now().plusSeconds(600));
        });
        when(this.thumbnails.copyTemplateUpload(anyLong(), anyString(), anyString())).thenAnswer(invocation ->
                new ThumbnailStorageService.FinalizedThumbnail(invocation.getArgument(2), "https://cdn.test.example.com/" + invocation.getArgument(2)));
    }

    @Test
    void ownersCanCreateNameUpdateReopenCloneAndDeleteWithoutChangingScenes() throws Exception {
        var created = send("POST", "/api/builder-templates", request("  Personal orb  ", document()), 201);
        long id = created.path("templateId").asLong();
        assertThat(created.path("name").asText()).isEqualTo("Personal orb");
        assertThat(created.path("revision").asLong()).isZero();
        assertThat(created.path("createdAt").asText()).isNotBlank();
        assertThat(send("GET", "/api/builder-templates", null, 200)).hasSize(1);
        assertThat(send("GET", path(id), null, 200)).isEqualTo(created);

        var scene = send("POST", "/api/scenes", request("Independent scene", created.path("sceneData")).set("tagIds", this.json.createArrayNode()), 201);
        var cloned = send("POST", "/api/builder-templates", request("Independent clone", created.path("sceneData")), 201);
        ObjectNode edited = (ObjectNode) created.path("sceneData").deepCopy();
        ((ObjectNode) edited.path("objects").get(0)).put("name", "Edited object");
        var updated = send("PUT", path(id), request("Updated orb", edited).put("expectedRevision", 0), 200);
        assertThat(updated.path("revision").asLong()).isEqualTo(1);
        assertThat(updated.path("sceneData")).isEqualTo(edited);
        var renamed = send("PATCH", path(id) + "/name", this.json.createObjectNode().put("name", "Renamed orb").put("expectedRevision", 1), 200);
        assertThat(renamed.path("revision").asLong()).isEqualTo(2);
        send("DELETE", path(id) + "?expectedRevision=2", null, 204);
        send("GET", path(id), null, 404);
        assertThat(send("GET", path(cloned.path("templateId").asLong()), null, 200).path("sceneData")).isEqualTo(created.path("sceneData"));
        assertThat(send("GET", "/api/scenes/" + scene.path("sceneId").asLong(), null, 200).path("sceneData")).isEqualTo(created.path("sceneData"));
    }

    @Test
    void anonymousAndOtherOwnersCannotReadChangeOrDeletePrivateDocuments() throws Exception {
        var created = send("POST", "/api/builder-templates", request("Private", document()), 201);
        String url = path(created.path("templateId").asLong());
        this.token = this.otherToken;
        assertThat(send("GET", "/api/builder-templates", null, 200)).isEmpty();
        send("GET", url, null, 404);
        send("PUT", url, request("Not yours", document()).put("expectedRevision", 0), 404);
        send("PATCH", url + "/name", this.json.createObjectNode().put("name", "No").put("expectedRevision", 0), 404);
        send("DELETE", url + "?expectedRevision=0", null, 404);
        send("POST", "/api/builder-templates", request("Private", document()), 201); // owner-local names
        this.token = null;
        send("GET", "/api/builder-templates", null, 401);
        send("GET", url, null, 401);
        send("POST", "/api/builder-templates", request("Anonymous", document()), 401);
        send("DELETE", url + "?expectedRevision=0", null, 401);
    }

    @Test
    void duplicateNamesAndStaleConcurrentWritesAreConflicts() throws Exception {
        var created = send("POST", "/api/builder-templates", request("Unique", document()), 201);
        long id = created.path("templateId").asLong();
        assertThat(send("POST", "/api/builder-templates", request("  UNIQUE  ", document()), 409).path("code").asText())
                .isEqualTo("BUILDER_TEMPLATE_NAME_EXISTS");
        var pending = new ArrayList<CompletableFuture<HttpResponse<String>>>();
        for (int i = 0; i < 5; i++) pending.add(this.client.sendAsync(http("PUT", path(id),
                request("Concurrent " + i, document()).put("expectedRevision", 0)), HttpResponse.BodyHandlers.ofString()));
        var statuses = pending.stream().map(CompletableFuture::join).map(HttpResponse::statusCode).toList();
        assertThat(statuses.stream().filter(s -> s == 200).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(s -> s == 409).count()).isEqualTo(4);
        send("DELETE", path(id) + "?expectedRevision=0", null, 409);
        send("PATCH", path(id) + "/name", this.json.createObjectNode().put("name", "Stale").put("expectedRevision", 0), 409);
        for (JsonNode bad : java.util.List.of(this.json.getNodeFactory().numberNode(-1), this.json.getNodeFactory().numberNode(0.5), this.json.getNodeFactory().textNode("1"))) {
            send("PUT", path(id), request("Bad revision", document()).set("expectedRevision", bad), 400);
        }
        send("PUT", path(id), request("Missing revision", document()), 400);
        send("DELETE", path(id), null, 400);
        assertThat(send("GET", path(id), null, 200).path("revision").asLong()).isEqualTo(1);
    }

    @Test
    void sharedContractBudgetsUnknownFieldsAndExecutableDocumentsCannotBypassValidation() throws Exception {
        var created = send("POST", "/api/builder-templates", request("Valid", document()), 201);
        String url = path(created.path("templateId").asLong());
        var invalids = new ArrayList<JsonNode>();
        invalids.add(document().put("schemaVersion", 99));
        invalids.add(document().put("builderVersion", 99));
        invalids.add(document().put("shader", "malicious()"));
        invalids.add(document().put("kind", "custom"));
        invalids.add(document().put("kind", "template"));
        ObjectNode badReference = document();
        ((ObjectNode) badReference.path("objects").get(0)).put("parentId", "missing");
        invalids.add(badReference);
        ObjectNode overBudget = document();
        for (int i = 1; i < 17; i++) overBudget.withArray("objects").add(((ObjectNode) overBudget.path("objects").get(0).deepCopy()).put("id", "object-" + i));
        invalids.add(overBudget);
        for (JsonNode invalid : invalids) {
            send("POST", "/api/builder-templates", request("Invalid", invalid), 400);
            send("PUT", url, request("Invalid", invalid).put("expectedRevision", 0), 400);
        }
        send("POST", "/api/builder-templates", request("Metadata", document()).put("description", "not a scene"), 400);
        send("POST", "/api/builder-templates", request("   ", document()), 400);
        send("POST", "/api/builder-templates", request("a".repeat(101), document()), 400);
        assertThat(send("GET", url, null, 200)).isEqualTo(created);
    }

    @Test
    void allFourteenCompleteBuilderRecipesSurvivePersonalTemplateStorage() throws Exception {
        try (var input = getClass().getResourceAsStream("/contracts/scenes/builder-catalog.v1.fixtures.json")) {
            var recipes = this.json.readTree(input).path("recipes");
            assertThat(recipes).hasSize(14);
            for (JsonNode recipe : recipes) {
                var created = send("POST", "/api/builder-templates", request(recipe.path("label").asText(), recipe.path("document")), 201);
                assertThat(created.path("sceneData")).as(recipe.path("label").asText())
                        .isEqualTo(new com.bdmage.mage_backend.validation.SceneDocumentValidator().validateAndNormalize(recipe.path("document")));
                assertThat(send("GET", path(created.path("templateId").asLong()), null, 200).path("sceneData"))
                        .as(recipe.path("label").asText()).isEqualTo(created.path("sceneData"));
            }
        }
    }

    @Test
    void uploadsAreOwnerScopedSingleUseAndCopiedToIndependentAssetsWithDurableCleanup() throws Exception {
        String upload = upload();
        String ownerToken = this.token;
        this.token = this.otherToken;
        send("POST", "/api/builder-templates", request("Stolen upload", document()).put("thumbnailObjectKey", upload), 400);
        this.token = ownerToken;
        var created = send("POST", "/api/builder-templates", request("Thumbnail", document()).put("thumbnailObjectKey", upload), 201);
        String url = path(created.path("templateId").asLong());
        String thumbnail = created.path("thumbnailRef").asText();
        assertThat(thumbnail).contains("/builder-templates/saved/").doesNotContain("/pending/");
        send("POST", "/api/builder-templates", request("Replay", document()).put("thumbnailObjectKey", upload), 400);
        send("POST", "/api/builder-templates", request("Scene asset", document()).put("thumbnailObjectKey", "scenes/1/thumbnails/image.png"), 400);
        var renamed = send("PATCH", url + "/name", this.json.createObjectNode().put("name", "Image retained").put("expectedRevision", 0), 200);
        assertThat(renamed.path("thumbnailRef").asText()).isEqualTo(thumbnail);
        String nextUpload = upload();
        when(this.thumbnails.copyTemplateUpload(anyLong(), eq(nextUpload), anyString()))
                .thenThrow(new ThumbnailStorageUnavailableException("Storage unavailable", new RuntimeException("test")));
        send("PUT", url, request("Failure", document()).put("expectedRevision", 1).put("thumbnailObjectKey", nextUpload), 503);
        assertThat(send("GET", url, null, 200)).isEqualTo(renamed);
        // Content changes without a fresh capture deliberately show a fallback instead of a stale thumbnail.
        var updated = send("PUT", url, request("Updated", document()).put("expectedRevision", 1), 200);
        assertThat(updated.path("thumbnailRef").isNull()).isTrue();
        doThrow(new ThumbnailStorageUnavailableException("Storage unavailable", new RuntimeException("test")))
                .doNothing().when(this.thumbnails).deleteTemplateAsset(thumbnail);
        this.cleanup.cleanup(); // failure leaves the deletion queued
        assertThat(this.jdbc.queryForObject("SELECT count(*) FROM builder_template_asset_cleanup WHERE thumbnail_ref = ?", Long.class, thumbnail)).isEqualTo(1);
        this.cleanup.cleanup();
        assertThat(this.jdbc.queryForObject("SELECT count(*) FROM builder_template_asset_cleanup WHERE thumbnail_ref = ?", Long.class, thumbnail)).isZero();
        verify(this.thumbnails, times(2)).deleteTemplateAsset(thumbnail);
        String abandoned = upload();
        send("DELETE", "/api/builder-templates/thumbnail/pending?objectKey=" + abandoned, null, 204);
        send("POST", "/api/builder-templates", request("Abandoned", document()).put("thumbnailObjectKey", abandoned), 400);
        this.jdbc.update("UPDATE builder_template_asset_cleanup SET not_before = CURRENT_TIMESTAMP");
        this.cleanup.cleanup();
        verify(this.thumbnails).deleteTemplateAsset(abandoned);
        send("DELETE", url + "?expectedRevision=2", null, 204);
    }

    private String upload() throws Exception {
        return send("POST", "/api/builder-templates/thumbnail/presign", this.json.createObjectNode()
                .put("filename", "capture.png").put("contentType", "image/png").put("sizeBytes", 100), 200).path("objectKey").asText();
    }

    private ObjectNode document() throws Exception {
        return (ObjectNode) this.json.readTree("""
                {"schemaVersion":1,"kind":"builder","builderVersion":1,"objects":[
                {"id":"orb","name":"Orb","operation":{"type":"sphere","radius":0.5}}]}
                """);
    }
    private ObjectNode request(String name, JsonNode document) {
        return this.json.createObjectNode().put("name", name).set("sceneData", document);
    }
    private String path(long id) { return "/api/builder-templates/" + id; }
    private String register() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 15);
        ObjectNode account = this.json.createObjectNode().put("email", suffix + "@example.test")
                .put("password", "SB14!" + suffix).put("firstName", "Builder").put("lastName", "Test")
                .put("displayName", "Builder test").put("handle", "@sb14_" + suffix);
        send("POST", "/api/auth/register", account, 201);
        return send("POST", "/api/auth/login", this.json.createObjectNode().put("email", account.path("email").asText())
                .put("password", account.path("password").asText()), 200).path("accessToken").asText();
    }
    private HttpRequest http(String method, String path, JsonNode body) {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + this.port + path))
                .timeout(Duration.ofSeconds(30)).header("Content-Type", "application/json");
        if (this.token != null) request.header("Authorization", "Bearer " + this.token);
        return request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body.toString())).build();
    }
    private JsonNode send(String method, String path, JsonNode body, int status) throws Exception {
        var response = this.client.send(http(method, path, body), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(method + " " + path + " " + response.body()).isEqualTo(status);
        if (path.startsWith("/api/builder-templates") && status == 200)
            assertThat(response.headers().firstValue("Cache-Control")).contains("private, no-store");
        return response.body().isBlank() ? this.json.nullNode() : this.json.readTree(response.body());
    }
}
