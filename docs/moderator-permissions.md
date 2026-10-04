# Moderator permissions (PP-R05)

Administrators can find an existing account and grant or revoke permission to disable/re-enable individual scenes. A moderator cannot manage people, use the global custom-rendering switch, bypass source restrictions, edit another owner's scene, or approve the isolated-renderer release. Ordinary owners retain their existing editing and repair access.

## Explicit administrator setup and recovery

`MAGE_ADMIN_USER_IDS` is a comma-separated set of existing numeric account IDs. It defaults to empty. These accounts authenticate normally; a nonexistent ID cannot authenticate and grants nobody access. Nonpositive or malformed IDs fail configuration binding. Verify the intended account IDs before deployment and keep this setting consistent across every backend instance.

Administrator access is never inferred from `MAGE_OPERATOR_USER_IDS`, a moderator grant, an email address, account order, or client-submitted role fields. There is no administrator promotion/demotion endpoint. The settings UI identifies administrator targets and does not offer moderator changes; the backend also rejects those mutations with HTTP 409. Thus this interface cannot remove the last administrator or make a moderator into an administrator.

To recover from an empty or incorrect administrator list, an authorized deployment operator must identify an existing account, correct `MAGE_ADMIN_USER_IDS`, and restart/redeploy all instances. Removing an administrator requires the same coordinated configuration change. A partial rollout leaves older instances with their previous administrator configuration. Do not edit grant/audit rows to recover administrator access.

## First deployment and legacy operator migration

1. Back up the complete database, including existing availability controls, and record the old application revision/configuration. Review the existing account IDs in the legacy operator list and explicitly choose administrator IDs; no previous operator is silently promoted.
2. Set `MAGE_ADMIN_USER_IDS` deliberately. Preserve the intended `MAGE_OPERATOR_USER_IDS` for the first PP-R05 startup only. Keep the custom-rendering release approval and persisted switch unchanged; this deployment does not approve custom rendering.
3. Stop old application instances before making PP-R05 authoritative. Old versions still use the allowlist at request time and would undermine revocation if left serving traffic during a mixed-version rollout.
4. Flyway V20 creates the grant table, immutable audit, and singleton import marker. Startup locks that marker and imports the legacy IDs in one transaction. Concurrent instances serialize on the same database lock. Existing explicit grant/revocation rows are preserved. Imported accounts receive only scene-moderator access, with an audit entry whose source is `legacy-allowlist` and administrator ID is null.
5. If any configured legacy ID does not identify an existing account, startup fails and the entire import transaction rolls back, including its audit and completion marker. Correct the list before restarting. Nonpositive IDs fail configuration validation. There is no partial successful import.
6. An empty legacy list also marks the import complete. Once complete, subsequent restarts or allowlist changes cannot grant access or restore a revoked grant. Remove the old setting after import and use the administrator UI/API for later assignments.
7. Verify capabilities with an administrator and ordinary account, grant a test moderator, verify individual scene access and global-switch denial, revoke the grant, then repeat the scene request with the same moderator token and confirm HTTP 403. Confirm the public rendering status is unchanged.

Both `docker-compose.yml` and `docker-compose.coolify.yml` forward the new administrator setting. No production account or release setting is assigned by this change. Database permissions are read from the primary database each request; do not route authorization to a lagging replica or add permission caches. A request already authorized before a revocation commits may finish; subsequent requests see the revoked grant without signing in again.

The migration is additive and preserves existing users/scenes/availability controls. Rolling application code back to a pre-PP-R05 version requires stopping all instances and clearing its legacy operator setting first, otherwise old allowlist permissions and global-switch authority would return. Keep V20 data and its completed marker when rolling forward again. Restore a backup only as a coordinated full database/application recovery, never by dropping the audit or resetting the marker in a running service.

## API contract

All routes use validated bearer authentication, derive the actor from that authentication, and send `Cache-Control: no-store`.

| Route | Access | Contract |
| --- | --- | --- |
| `GET /api/admin/capabilities` | Any authenticated account | `canModerateScenes`, `canManageModerators`, `canManageCustomRendering` booleans |
| `GET /api/admin/moderators/users?query=...` | Administrator | Exact positive ID, email, or handle (optional `@`); maximum 320 characters; zero/one result |
| `PUT /api/admin/moderators/users/{id}` | Administrator | `{enabled, expectedRevision, requestId, reason}` |
| `GET /api/admin/moderators/audit?beforeId=...&limit=20` | Administrator | Descending ID cursor; limit 1–50; `entries` and nullable `nextCursor` |

Lookup returns `{users, nextCursor:null}`. Each user has `userId`, `displayName`, `handle`, `email`, `sceneModerator`, `revision`, and `isAdministrator`. It never serializes the account entity, password hashes, authentication-provider identifiers, or tokens. Emails are visible only to administrators through exact lookup and mutation responses; audit responses contain numeric actor/target IDs rather than those private snapshots.

Mutation requires a boolean `enabled`, nonnegative `expectedRevision`, UUID `requestId`, and nonblank reason of at most 1000 characters. Unknown fields such as `isAdministrator` are rejected. Changed status increments the revision; an accepted same-status request retains the revision but records one receipt. The returned object is the same minimal user shape.

Fresh requests lock the administrator's UUID namespace and the target account row in one transaction. This serializes conflicting first grants, stale edits, and UUID reuse across different targets. Duplicate requests with the same actor, UUID, and normalized payload return the original response snapshot without reapplying changes. A later revocation therefore stays revoked even when an earlier grant is retried. Authorization is checked before replay. Clients should refetch current state after success and must refetch and reconfirm after HTTP 409 rather than automatically retrying against a new revision.

Errors are 401 for missing/invalid authentication, 403 for insufficient capability, 404 `USER_NOT_FOUND` for a missing target, 400 for malformed/invalid input, and 409 `MODERATOR_CONFLICT` for stale revisions, conflicting UUID reuse, or an administrator target. Conflict responses contain a safe message rather than user details. Moderator access to the global custom-rendering endpoint remains 403; even an administrator still receives the existing release-gate 409 when trying to enable custom rendering before approval.

## Audit and verification

Every accepted assignment/revocation stores actor, target, previous/new status, revision, reason, UUID, server timestamp, and identity snapshots in the same transaction as the grant. The database trigger rejects UPDATE and DELETE on audit rows, so deleting an account cannot rewrite its historical IDs. Normal entries have `source: administrator`; one-time imports have `source: legacy-allowlist` and null administrator ID. Audit pagination exposes `id`, `administratorUserId`, `targetUserId`, `previousEnabled`, `enabled`, `revision`, `reason`, `requestId`, `changedAt`, and `source`.

Run `mvn test` (or `./mvnw test`) with Docker available. `ModeratorPermissionsIntegrationTests` exercises real PostgreSQL migrations, private lookup/capability boundaries, grant/revoke with existing tokens, unchanged release gating, administrator-target rejection, immutable audit, cursor validation, stale writes, concurrent first grants, concurrent duplicate/cross-target UUID use, and transactional one-time import/restart behavior. Existing scene-availability and release-gate tests continue to cover owner/source restrictions and fail-closed playback.
