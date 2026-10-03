# Scene availability and operator controls

PP-R02 adds backend controls for disabling a scene for everyone and stopping all custom rendering. It includes APIs and this operator runbook. The management UI, renderer guards, and live browser polling belong to frontend story PP-R03.

## Release defaults and rollout

Custom rendering starts **disabled**. Both conditions must be met to enable it:

1. PP-I03 isolation release checks have passed, and deployment configuration explicitly sets `MAGE_CUSTOM_RENDERING_RELEASE_APPROVED=true`.
2. An authorized operator explicitly enables the persisted switch through the admin API.

Setting the environment flag alone does not enable rendering. A missing environment flag, missing database switch row, or false value keeps it disabled. If an already-enabled deployment changes the release flag to false, public availability becomes disabled even though the admin response still shows the stored `enabled` value. Set the stored switch to false before restoring the release flag if a new operator enable should be required.

**All documents currently accepted by this backend are legacy/custom scenes.** Backend validation of trusted template references belongs to PP-B02. Client-supplied `kind` or template metadata cannot bypass the switch. Consequently, with the default settings, all existing scene responses retain their metadata and thumbnail but return `sceneData: null`. Stored content is preserved. Older frontends need PP-R03 to present this state clearly and stop already-running scenes.

Before rollout, coordinate the frontend release and remove any previously cached source-bearing API responses from the reverse proxy/CDN. Do not enable custom rendering merely to preserve old playback before isolation approval.

## Configure authorized operators

Set `MAGE_OPERATOR_USER_IDS` to a comma-separated list of existing numeric user IDs, for example `12,34`, and restart the backend instances. The default is empty: no account can manage these controls. Keep this list consistent across instances. Removing an ID takes effect on instances after their configuration reload/restart.

Operators authenticate through the existing login flow and send `Authorization: Bearer <accessToken>`. Ownership alone never grants operator privileges. Actor IDs come from the validated token, never from request JSON. An operator account must still exist and authenticate normally.

Both Compose deployment definitions forward the allowlist and release gate variables. `.env.example` documents their defaults. Neither is a public frontend setting.

## API contract

All the following endpoints and scene/profile responses use `Cache-Control: no-store`.

| Method and route | Access | Result |
| --- | --- | --- |
| `GET /api/scene-availability/{id}` | Public | Effective availability for one scene, without source or private audit |
| `GET /api/scene-availability?ids=1,2` | Public | Availability for 1–100 positive IDs; duplicates collapsed in first-occurrence order |
| `GET /api/rendering-status` | Public | Effective global custom-rendering state |
| `GET /api/admin/scenes/{id}/availability` | Operator | Stored scene control with last-change audit |
| `PUT /api/admin/scenes/{id}/availability` | Operator | Disable or re-enable the scene |
| `GET /api/admin/rendering/custom` | Operator | Stored global switch, release approval, and last-change audit |
| `PUT /api/admin/rendering/custom` | Operator | Enable or disable custom rendering |
| `GET /api/scenes/{id}/repair` | Scene owner | Explicit source access for editing, always `playable: false` |

Public scene status:

```json
{
  "sceneId": 23,
  "available": false,
  "code": "SCENE_DISABLED",
  "message": "This scene is unavailable."
}
```

Public codes are `AVAILABLE`, `SCENE_DISABLED`, `CUSTOM_RENDERING_DISABLED`, and `SCENE_NOT_FOUND`. Available status has a null message. Missing IDs return an unavailable status with HTTP 200, including in batches; malformed, missing, empty, nonpositive, or more than 100 requested IDs return HTTP 400. Scene disablement takes precedence over the global switch in the scene status code.

Global public status returns `enabled`, `code`, and `message`. It reports the effective state: persisted enabled **and** release approved. Operator global status separately returns stored `enabled` and `releaseApproved`.

Every list/detail/create/update/description/thumbnail-finalize response includes an `availability` object. When unavailable, the entire `sceneData` field is null, including for the owner or an operator. Lists, tag-filtered discovery, user scene lists, and public profile collections use the same check. Metadata and thumbnails remain visible; disablement does not delete or change scene visibility.

## Disable and re-enable a scene

Send `PUT /api/admin/scenes/23/availability` with the operator bearer token and JSON:

```json
{"disabled": true, "reason": "Rendering failure confirmed during investigation."}
```

The response contains `sceneId`, `disabled`, `changedByUserId`, `changedAt`, and `reason`. To re-enable, send the same route with `disabled: false` and a reason describing the resolution. Re-enabling a scene does not override the global switch or release gate. Check its public status afterward.

`reason` is required, nonblank, and at most 1000 characters for both directions. The value is trimmed. Audit details appear only on operator routes; public messages never include operator identity or reason. The database retains the **latest state transition**, not a full event history. Retrying the same state leaves its existing audit unchanged. Setting an already-available scene to available is a no-op with no fabricated change audit. If the operator's account is deleted, the state, timestamp, and reason remain, but `changedByUserId` becomes null.

## Emergency custom-rendering switch

Send `PUT /api/admin/rendering/custom` with:

```json
{"enabled": false, "reason": "Emergency pause while investigating rendering failures."}
```

This immediately changes fresh server responses across instances sharing the database. Individual scene blocks remain intact. After isolation approval and incident resolution, an operator may use the same route with `enabled: true` and a reason. If the release flag is false, enabling returns HTTP 409 `CUSTOM_RENDERING_RELEASE_REQUIRED`.

After either action, verify `GET /api/rendering-status` and a relevant scene's public status. Repeated same-state updates preserve the last-change audit.

## Owner repair and persistence boundaries

The owner can retrieve the original content through `GET /api/scenes/{id}/repair` using their bearer token. Its separate response includes source, metadata, current availability, and `playable: false`. The frontend must use this as editing data and must not feed it directly into a renderer. Operator status alone does not grant access to another owner's repair content.

Owners save repairs using existing scene update routes and existing submission validation. A separate availability table prevents content replacement, imported JSON, description changes, or thumbnail updates from clearing an operator block. Unknown request fields cannot set the control. An individually blocked scene requires an operator to re-enable that same ID before it becomes publicly playable. A scene paused only by the global switch instead awaits the global switch and release gate.

There are currently no server fork/restore endpoints. Future ones must use the shared response factory and preserve the control for an existing ID. Detecting copied source uploaded as a new scene ID is outside this story. Deletion removes the scene and its control; restoring a full database must restore control tables as well as scene content. Do not restore only scene rows into a running deployment.

## Propagation and caching

Availability is read directly from PostgreSQL on each request, outside the managed scene entity state; it has no application cache to invalidate. Batch checks query only IDs and controls, not shader source. Administrative writes are atomic, and successful committed changes are visible to subsequent reads on the primary database. Serve these endpoints from that database, not a lagging replica, and do not add proxy caching or 304 responses for status/source-bearing routes.

PP-R03 clients should check status before starting a scene, poll active scenes in batches at most every 10 seconds, and recheck on reconnect/focus/resume. They must stop affected rendering within the story's 30-second online window and fail closed on unavailable, failed, or stale status. The backend exposes the fresh status contract; it cannot interrupt an already-running browser by itself.

No server control can erase source already downloaded, exported, copied, or held offline. Previously cached local scenes must go through the frontend status guard when online playback resumes. Owner repair access is explicit and intentionally still exposes the owner's source.

## Failure responses and verification

- Missing or malformed bearer authentication: HTTP 401 `AUTHENTICATION_REQUIRED`; an expired or invalid token returns HTTP 401 `INVALID_AUTH_TOKEN`.
- Authenticated account outside the operator allowlist: HTTP 403 `OPERATOR_ACCESS_REQUIRED`.
- Non-owner repair request: HTTP 403 `SCENE_OWNERSHIP_REQUIRED`.
- Missing scene on admin/repair routes: HTTP 404 `SCENE_NOT_FOUND`.
- Invalid IDs, missing booleans, or invalid reasons: HTTP 400 `VALIDATION_ERROR`; unparseable request JSON returns HTTP 400 `MALFORMED_REQUEST`.
- Enable before isolation approval: HTTP 409 `CUSTOM_RENDERING_RELEASE_REQUIRED`.

Run `./mvnw test` (Windows: `.\mvnw.cmd test`) with Docker available. Coverage includes operator/owner boundaries, every current source-bearing response surface, source suppression, same-ID repair persistence, idempotent audit, bounded status requests, default/missing switch behavior, release gating, re-enablement, no-store responses, and migration behavior. Integration tests explicitly opt into release approval only where they test operator enabling; ordinary test configuration remains disabled.
