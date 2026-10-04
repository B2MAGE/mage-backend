package com.bdmage.mage_backend.controller;

import java.util.*;
import java.util.concurrent.*;
import com.bdmage.mage_backend.dto.UpdateModeratorRequest;
import com.bdmage.mage_backend.exception.ModeratorConflictException;
import com.bdmage.mage_backend.exception.OperatorAccessRequiredException;
import com.bdmage.mage_backend.model.Scene;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.repository.*;
import com.bdmage.mage_backend.service.*;
import com.bdmage.mage_backend.support.PostgresIntegrationTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(properties = "mage.administration.user-ids=900000051,900000052")
@AutoConfigureMockMvc
@Testcontainers
class ModeratorPermissionsIntegrationTests extends PostgresIntegrationTestSupport {
    private static final long ADMIN = 900000051L, OTHER_ADMIN = 900000052L;
    private final ObjectMapper json = new ObjectMapper();
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository users;
    @Autowired private SceneRepository scenes;
    @Autowired private ModeratorPermissionRepository repository;
    @Autowired private ModeratorPermissionService service;
    @Autowired private OperatorAccessService access;
    @Autowired private AuthenticationTokenService tokens;
    private User target;
    private String adminToken, targetToken;

    @BeforeEach void identities() {
        for (long id : List.of(ADMIN, OTHER_ADMIN)) {
            jdbc.update("""
                INSERT INTO users(id,email,password_hash,display_name,first_name,last_name,handle)
                VALUES (?,?,'secret-hash','Administrator','Admin','',?) ON CONFLICT(id) DO NOTHING
                """, id, "admin" + id + "@example.com", "admin" + id);
        }
        target = user();
        adminToken = tokens.issueToken(users.findById(ADMIN).orElseThrow());
        targetToken = tokens.issueToken(target);
    }

    @Test void capabilityDiscoveryAndPrivateLookupNeverExposeCredentials() throws Exception {
        mvc.perform(get("/api/admin/capabilities")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/capabilities").header("Authorization", bearer(targetToken)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.canModerateScenes").value(false))
            .andExpect(jsonPath("$.canManageModerators").value(false)).andExpect(jsonPath("$.canManageCustomRendering").value(false));
        mvc.perform(get("/api/admin/capabilities").header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.canModerateScenes").value(true))
            .andExpect(jsonPath("$.canManageModerators").value(true)).andExpect(jsonPath("$.canManageCustomRendering").value(true));
        for (String query : List.of(target.getId().toString(), target.getEmail().toUpperCase(), "@" + target.getHandle())) {
            var response = mvc.perform(get("/api/admin/moderators/users").param("query", query).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.users[0].userId").value(target.getId())).andExpect(jsonPath("$.users[0].revision").value(0))
                .andExpect(jsonPath("$.users[0].isAdministrator").value(false)).andReturn().getResponse().getContentAsString();
            assertThat(response).doesNotContain("password", "googleSubject", "secret-hash", "authProvider");
            assertThat(json.readTree(response).path("users").get(0).size()).isEqualTo(7);
        }
        mvc.perform(get("/api/admin/moderators/users").param("query", "" + ADMIN).header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.users[0].isAdministrator").value(true));
        mvc.perform(get("/api/admin/moderators/users").param("query", "does_not_exist").header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.users").isEmpty());
    }

    @Test void grantAllowsSceneControlButNotGlobalControlOrManagingUsersAndRevokeAppliesToExistingToken() throws Exception {
        Scene scene = scenes.saveAndFlush(new Scene(target.getId(), "Moderation test", json.createObjectNode()));
        updateHttp(target.getId(), true, 0, UUID.randomUUID(), "Grant for scene reviews", adminToken, 200);
        mvc.perform(get("/api/admin/capabilities").header("Authorization", bearer(targetToken)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.canModerateScenes").value(true))
            .andExpect(jsonPath("$.canManageModerators").value(false)).andExpect(jsonPath("$.canManageCustomRendering").value(false));
        mvc.perform(get("/api/admin/scenes/" + scene.getId() + "/availability").header("Authorization", bearer(targetToken))).andExpect(status().isOk());
        mvc.perform(put("/api/admin/scenes/" + scene.getId() + "/availability").header("Authorization", bearer(targetToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"disabled\":true,\"reason\":\"Confirmed rendering issue\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.disabled").value(true));
        for (String path : List.of("/api/admin/rendering/custom", "/api/admin/moderators/audit", "/api/admin/moderators/users?query=" + target.getId())) {
            mvc.perform(get(path).header("Authorization", bearer(targetToken))).andExpect(status().isForbidden());
        }
        mvc.perform(put("/api/admin/rendering/custom").header("Authorization", bearer(targetToken))
            .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true,\"reason\":\"Attempt escalation\"}"))
            .andExpect(status().isForbidden());
        updateHttp(user().getId(), true, 0, UUID.randomUUID(), "Attempt escalation", targetToken, 403);
        updateHttp(target.getId(), false, 1, UUID.randomUUID(), "Assignment ended", adminToken, 200);
        mvc.perform(get("/api/admin/scenes/" + scene.getId() + "/availability").header("Authorization", bearer(targetToken))).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/scenes/" + scene.getId() + "/availability").header("Authorization", bearer(targetToken))
            .contentType(MediaType.APPLICATION_JSON).content("{\"disabled\":false,\"reason\":\"Try after revoke\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/scene-availability/" + scene.getId())).andExpect(jsonPath("$.code").value("SCENE_DISABLED"));
        mvc.perform(get("/api/rendering-status")).andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(put("/api/admin/rendering/custom").header("Authorization", bearer(adminToken))
            .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true,\"reason\":\"Cannot bypass release\"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CUSTOM_RENDERING_RELEASE_REQUIRED"));
    }

    @Test void preventsSelfPromotionAdministratorDemotionAndMalformedRequests() throws Exception {
        updateHttp(target.getId(), true, 0, UUID.randomUUID(), "Self promotion", targetToken, 403);
        for (long id : List.of(ADMIN, OTHER_ADMIN)) updateHttp(id, false, 0, UUID.randomUUID(), "Cannot demote admin", adminToken, 409);
        assertThat(access.isAdministrator(ADMIN)).isTrue();
        assertThat(access.isAdministrator(OTHER_ADMIN)).isTrue();
        String valid = body(true, 0, UUID.randomUUID(), "Reason");
        for (String invalid : List.of(valid.replace("}", ",\"isAdministrator\":true}"), valid.replace("true", "null"),
                valid.replace("\"expectedRevision\":0", "\"expectedRevision\":-1"), valid.replace("Reason", " "),
                valid.replace("Reason", "x".repeat(1001)), valid.replaceFirst("[a-f0-9-]{36}", "invalid"))) {
            mvc.perform(put(path(target.getId())).header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest());
        }
        updateHttp(Long.MAX_VALUE, true, 0, UUID.randomUUID(), "Unknown account", adminToken, 404);
        mvc.perform(get("/api/admin/moderators/users").param("query", "x".repeat(321)).header("Authorization", bearer(adminToken))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/moderators/audit").param("limit", "51").header("Authorization", bearer(adminToken))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/moderators/audit").param("beforeId", "0").header("Authorization", bearer(adminToken))).andExpect(status().isBadRequest());
        assertThat(auditCount(target.getId())).isZero();
    }

    @Test void staleWritesAndReplayedReceiptsCannotRestoreRevokedPermission() throws Exception {
        UUID requestId = UUID.randomUUID();
        var original = service.update(ADMIN, target.getId(), request(true, 0, requestId));
        assertThat(original.revision()).isEqualTo(1);
        assertThat(service.update(ADMIN, target.getId(), request(true, 0, requestId))).isEqualTo(original);
        assertThat(auditCount(target.getId())).isEqualTo(1);
        assertThatThrownBy(() -> service.update(OTHER_ADMIN, target.getId(), request(false, 0, UUID.randomUUID()))).isInstanceOf(ModeratorConflictException.class);
        service.update(OTHER_ADMIN, target.getId(), request(false, 1, UUID.randomUUID()));
        assertThat(service.update(ADMIN, target.getId(), request(true, 0, requestId))).isEqualTo(original);
        assertThat(repository.isModerator(target.getId())).isFalse();
        assertThat(auditCount(target.getId())).isEqualTo(2);
        assertThatThrownBy(() -> service.update(ADMIN, user().getId(), request(true, 0, requestId))).isInstanceOf(ModeratorConflictException.class);
        assertThatThrownBy(() -> service.update(ADMIN, target.getId(), request(false, 0, requestId))).isInstanceOf(ModeratorConflictException.class);
        assertThatThrownBy(() -> service.update(target.getId(), target.getId(), request(true, 0, requestId))).isInstanceOf(OperatorAccessRequiredException.class);
        var noop = service.update(ADMIN, target.getId(), request(false, 2, UUID.randomUUID()));
        assertThat(noop.revision()).isEqualTo(2);
        assertThat(auditCount(target.getId())).isEqualTo(3);
    }

    @Test void permissionMutationRejectsScalarCoercionWithoutTouchingTheGrantOrAudit() throws Exception {
        String id = UUID.randomUUID().toString();
        for (String fields : List.of(
                "\"enabled\":\"true\",\"expectedRevision\":0,\"reason\":\"Reason\",\"requestId\":\"" + id + "\"",
                "\"enabled\":1,\"expectedRevision\":0,\"reason\":\"Reason\",\"requestId\":\"" + id + "\"",
                "\"enabled\":true,\"expectedRevision\":0.9,\"reason\":\"Reason\",\"requestId\":\"" + id + "\"",
                "\"enabled\":true,\"expectedRevision\":0.0,\"reason\":\"Reason\",\"requestId\":\"" + id + "\"",
                "\"enabled\":true,\"expectedRevision\":\"0\",\"reason\":\"Reason\",\"requestId\":\"" + id + "\"",
                "\"enabled\":true,\"expectedRevision\":9223372036854775808,\"reason\":\"Reason\",\"requestId\":\"" + id + "\"",
                "\"enabled\":true,\"expectedRevision\":0,\"reason\":123,\"requestId\":\"" + id + "\"",
                "\"enabled\":true,\"expectedRevision\":0,\"reason\":\"Reason\",\"requestId\":123",
                "\"enabled\":true,\"expectedRevision\":0,\"reason\":\"Reason\",\"requestId\":\"1-1-1-1-1\"")) {
            mvc.perform(put(path(target.getId())).header("Authorization", bearer(adminToken))
                    .contentType(MediaType.APPLICATION_JSON).content("{" + fields + "}"))
                .andExpect(status().isBadRequest());
        }
        assertThat(repository.hasGrantRow(target.getId())).isFalse();
        assertThat(auditCount(target.getId())).isZero();
    }

    @Test void auditIsPrivatePaginatedAndDatabaseRejectsRewritingHistory() throws Exception {
        service.update(ADMIN, target.getId(), request(true, 0, UUID.randomUUID()));
        service.update(OTHER_ADMIN, target.getId(), request(false, 1, UUID.randomUUID()));
        var result = mvc.perform(get("/api/admin/moderators/audit").param("limit", "1").header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", containsString("no-store")))
            .andExpect(jsonPath("$.entries[0].administratorUserId").value(OTHER_ADMIN)).andExpect(jsonPath("$.entries[0].targetUserId").value(target.getId()))
            .andExpect(jsonPath("$.entries[0].previousEnabled").value(true)).andExpect(jsonPath("$.entries[0].enabled").value(false))
            .andExpect(jsonPath("$.entries[0].revision").value(2)).andExpect(jsonPath("$.entries[0].source").value("administrator"))
            .andExpect(jsonPath("$.entries[0].changedAt").isString()).andReturn().getResponse().getContentAsString();
        long cursor = json.readTree(result).path("nextCursor").asLong();
        mvc.perform(get("/api/admin/moderators/audit").param("limit", "1").param("beforeId", "" + cursor).header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.entries[0].administratorUserId").value(ADMIN));
        assertThatThrownBy(() -> jdbc.update("UPDATE scene_moderator_audit SET reason='rewritten' WHERE target_user_id=?", target.getId())).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM scene_moderator_audit WHERE target_user_id=?", target.getId())).isInstanceOf(DataAccessException.class);
        assertThat(auditCount(target.getId())).isEqualTo(2);
    }

    @Test void concurrentFirstGrantsSerializeAgainstTheSameRevision() throws Exception {
        var results = concurrently(() -> service.update(ADMIN, target.getId(), request(true, 0, UUID.randomUUID())),
            () -> service.update(OTHER_ADMIN, target.getId(), request(true, 0, UUID.randomUUID())));
        assertThat(results.stream().filter(ModeratorConflictException.class::isInstance)).hasSize(1);
        assertThat(repository.findUser(target.getId()).orElseThrow().revision()).isEqualTo(1);
        assertThat(auditCount(target.getId())).isEqualTo(1);
    }

    @Test void concurrentDuplicateUuidReturnsOneReceiptAndCrossTargetReuseConflicts() throws Exception {
        UUID requestId = UUID.randomUUID();
        var same = request(true, 0, requestId);
        var duplicate = concurrently(() -> service.update(ADMIN, target.getId(), same), () -> service.update(ADMIN, target.getId(), same));
        assertThat(duplicate.get(0)).isEqualTo(duplicate.get(1));
        assertThat(auditCount(target.getId())).isEqualTo(1);
        User first = user(), second = user();
        UUID reused = UUID.randomUUID();
        var conflict = concurrently(() -> service.update(ADMIN, first.getId(), request(true, 0, reused)),
            () -> service.update(ADMIN, second.getId(), request(true, 0, reused)));
        assertThat(conflict.stream().filter(ModeratorConflictException.class::isInstance)).hasSize(1);
        assertThat(auditCount(first.getId()) + auditCount(second.getId())).isEqualTo(1);
    }

    @Test void oneTimeLegacyImportCannotReintroduceRevokedGrantsOrPromoteAdministrators() throws Exception {
        jdbc.update("UPDATE scene_moderator_bootstrap SET completed_at=NULL WHERE id=1");
        var imports = concurrently(() -> { service.importLegacyAllowlist(Set.of(target.getId())); return true; },
            () -> { service.importLegacyAllowlist(Set.of(target.getId())); return true; });
        assertThat(imports).containsExactly(true, true);
        assertThat(access.isOperator(target.getId())).isTrue();
        assertThat(access.isAdministrator(target.getId())).isFalse();
        assertThat(auditCount(target.getId())).isEqualTo(1);
        var importAudit = service.audit(ADMIN, null, 1).entries().getFirst();
        assertThat(importAudit.source()).isEqualTo("legacy-allowlist");
        assertThat(importAudit.administratorUserId()).isNull();
        service.update(ADMIN, target.getId(), request(false, 1, UUID.randomUUID()));
        User late = user();
        service.importLegacyAllowlist(Set.of(target.getId(), late.getId()));
        assertThat(repository.isModerator(target.getId())).isFalse();
        assertThat(repository.isModerator(late.getId())).isFalse();
        // Even a controlled marker replay preserves explicit rows, including revocations.
        jdbc.update("UPDATE scene_moderator_bootstrap SET completed_at=NULL WHERE id=1");
        service.importLegacyAllowlist(Set.of(target.getId()));
        assertThat(repository.isModerator(target.getId())).isFalse();
        assertThat(auditCount(target.getId())).isEqualTo(2);
    }

    @Test void invalidLegacyAccountRollsBackWholeImportAndEmptyImportIsFinal() {
        jdbc.update("UPDATE scene_moderator_bootstrap SET completed_at=NULL WHERE id=1");
        assertThatThrownBy(() -> service.importLegacyAllowlist(Set.of(target.getId(), Long.MAX_VALUE))).isInstanceOf(IllegalStateException.class);
        assertThat(repository.hasGrantRow(target.getId())).isFalse();
        assertThat(auditCount(target.getId())).isZero();
        assertThat(jdbc.queryForObject("SELECT completed_at IS NULL FROM scene_moderator_bootstrap WHERE id=1", Boolean.class)).isTrue();
        service.importLegacyAllowlist(Set.of());
        service.importLegacyAllowlist(Set.of(target.getId()));
        assertThat(repository.isModerator(target.getId())).isFalse();
        assertThat(jdbc.queryForObject("SELECT completed_at IS NOT NULL FROM scene_moderator_bootstrap WHERE id=1", Boolean.class)).isTrue();
    }

    private User user() {
        String key = "r05" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        return users.saveAndFlush(new User(key + "@example.com", "secret-hash", "User", "", "Test user", key));
    }
    private long auditCount(long id) { return jdbc.queryForObject("SELECT count(*) FROM scene_moderator_audit WHERE target_user_id=?", Long.class, id); }
    private static String bearer(String token) { return "Bearer " + token; }
    private static String path(long id) { return "/api/admin/moderators/users/" + id; }
    private static UpdateModeratorRequest request(boolean enabled, long revision, UUID id) { return new UpdateModeratorRequest(enabled, revision, id, "Test assignment"); }
    private String body(boolean enabled, long revision, UUID id, String reason) throws Exception {
        return json.writeValueAsString(Map.of("enabled", enabled, "expectedRevision", revision, "requestId", id.toString(), "reason", reason));
    }
    private void updateHttp(long targetId, boolean enabled, long revision, UUID id, String reason, String token, int expected) throws Exception {
        mvc.perform(put(path(targetId)).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON).content(body(enabled, revision, id, reason)))
            .andExpect(status().is(expected));
    }
    private static List<Object> concurrently(Callable<Object> first, Callable<Object> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> runTogether(first, ready, start));
            var b = executor.submit(() -> runTogether(second, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            return List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
        }
    }
    private static Object runTogether(Callable<Object> task, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown(); if (!start.await(10, TimeUnit.SECONDS)) throw new TimeoutException();
        try { return task.call(); } catch (ModeratorConflictException ex) { return ex; }
    }
}
