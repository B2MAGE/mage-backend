package com.bdmage.mage_backend.service;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import com.bdmage.mage_backend.dto.ModeratorAuditResponse;
import com.bdmage.mage_backend.dto.ModeratorUserResponse;
import com.bdmage.mage_backend.dto.UpdateModeratorRequest;
import com.bdmage.mage_backend.exception.InvalidSceneAvailabilityRequestException;
import com.bdmage.mage_backend.exception.ModeratorConflictException;
import com.bdmage.mage_backend.exception.ModeratorUserNotFoundException;
import com.bdmage.mage_backend.repository.ModeratorPermissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModeratorPermissionService {
    private final OperatorAccessService access;
    private final ModeratorPermissionRepository permissions;
    public ModeratorPermissionService(OperatorAccessService access, ModeratorPermissionRepository permissions) {
        this.access = access; this.permissions = permissions;
    }
    public record Users(List<ModeratorUserResponse> users, Long nextCursor) {}
    public record Audit(List<ModeratorAuditResponse> entries, Long nextCursor) {}

    @Transactional(readOnly = true)
    public Users lookup(Long actor, String query) {
        access.requireAdministrator(actor);
        if (query == null || query.isBlank() || query.length() > 320) throw invalid();
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        if (normalized.matches("[0-9]+")) {
            try {
                long id = Long.parseLong(normalized);
                if (id <= 0) throw invalid();
                return new Users(permissions.findUser(id).map(this::identifyAdministrator).map(List::of).orElse(List.of()), null);
            } catch (NumberFormatException ex) { throw invalid(); }
        }
        if (normalized.startsWith("@")) normalized = normalized.substring(1);
        boolean email = normalized.contains("@");
        if (email ? !normalized.matches("[^\\s@]+@[^\\s@]+") : !normalized.matches("[a-z][a-z0-9_]{2,29}")) throw invalid();
        return new Users(permissions.lookup(normalized, email).stream().map(this::identifyAdministrator).toList(), null);
    }

    @Transactional
    public ModeratorUserResponse update(Long actor, long target, UpdateModeratorRequest request) {
        // Reauthorize even idempotent retries; the stored result is never an authorization grant.
        access.requireAdministrator(actor);
        if (target <= 0 || request == null || request.enabled() == null || request.expectedRevision() == null
                || request.expectedRevision() < 0 || request.requestId() == null || request.reason() == null
                || request.reason().isBlank() || request.reason().length() > 1000) throw invalid();
        String reason = request.reason().trim();
        if (access.isAdministrator(target)) throw new ModeratorConflictException("Administrator access is managed separately and cannot be changed here.");
        permissions.lockAdministrator(actor);
        var replay = permissions.replay(actor, request.requestId());
        if (replay.isPresent()) {
            var prior = replay.get();
            if (prior.targetId() != target || prior.enabled() != request.enabled()
                    || prior.expectedRevision() != request.expectedRevision() || !prior.reason().equals(reason)) throw new ModeratorConflictException();
            return prior.response();
        }
        if (!permissions.lockUser(target)) throw new ModeratorUserNotFoundException();
        var before = permissions.findUser(target).orElseThrow(ModeratorUserNotFoundException::new);
        if (before.revision() != request.expectedRevision()) throw new ModeratorConflictException();
        long revision = before.revision() + (before.sceneModerator() == request.enabled() ? 0 : 1);
        var after = new ModeratorUserResponse(target, before.displayName(), before.handle(), before.email(), request.enabled(), revision, false);
        permissions.save(target, after.sceneModerator(), after.revision());
        // Accepted no-op requests also get one immutable receipt, making UUID reuse unambiguous.
        permissions.appendAudit(actor, before, after, request.requestId(), reason, "administrator");
        return after;
    }

    @Transactional(readOnly = true)
    public Audit audit(Long actor, Long beforeId, int limit) {
        access.requireAdministrator(actor);
        if ((beforeId != null && beforeId <= 0) || limit < 1 || limit > 50) throw invalid();
        var rows = permissions.audit(beforeId, limit + 1);
        boolean more = rows.size() > limit;
        var entries = List.copyOf(rows.subList(0, Math.min(limit, rows.size())));
        return new Audit(entries, more ? entries.getLast().id() : null);
    }

    /** One database-wide transaction, never a continuing configuration permission fallback. */
    @Transactional
    public void importLegacyAllowlist(Set<Long> ids) {
        if (permissions.lockBootstrapCompleted()) return;
        for (long id : ids.stream().sorted().toList()) {
            if (!permissions.lockUser(id)) throw new IllegalStateException("Legacy operator ID does not identify an existing account: " + id);
            // Preserve an explicit grant or revocation written during a rollout.
            if (permissions.hasGrantRow(id)) continue;
            var before = permissions.findUser(id).orElseThrow();
            var after = new ModeratorUserResponse(id, before.displayName(), before.handle(), before.email(), true, 1, false);
            permissions.save(id, true, 1);
            permissions.appendAudit(null, before, after, UUID.randomUUID(), "Imported existing scene operator permission during PP-R05 migration.", "legacy-allowlist");
        }
        permissions.completeBootstrap();
    }

    private static InvalidSceneAvailabilityRequestException invalid() {
        return new InvalidSceneAvailabilityRequestException("Enter a valid account identifier, permission revision, request ID and reason.");
    }

    private ModeratorUserResponse identifyAdministrator(ModeratorUserResponse user) {
        return new ModeratorUserResponse(user.userId(), user.displayName(), user.handle(), user.email(),
                user.sceneModerator(), user.revision(), access.isAdministrator(user.userId()));
    }
}
