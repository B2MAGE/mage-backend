# Scene documents and legacy upgrades (PP-B02)

`POST /api/scenes` and `PUT /api/scenes/{id}` require an explicit version-one
document in `sceneData`. Bare engine objects are no longer accepted for writes.
`SceneDocumentValidator` applies the checked-in PP-B01 schema/catalog, fills
documented template defaults, and reuses PP-V01 resource limits before any
entity mutation, thumbnail finalization, or playlist attachment.

## Write contract

Custom source remains untrusted, regardless of creator role or shader similarity:

```json
{"name":"My scene","sceneData":{"schemaVersion":1,"kind":"custom","scene":{"visualizer":{"shader":"sphere(0.5);"}}}}
```

Templates refer only to the 16 immutable catalog entries; source is not accepted
in a template document:

```json
{"name":"My template","sceneData":{"schemaVersion":1,"kind":"template","templateId":"embedded-scene-0","templateVersion":1,"parameters":{"scale":10,"speed":1}}}
```

The exact schema and catalog live in `src/main/resources/contracts/scenes/`.
Unknown versions, template IDs, root/nested fields, mixed template/source data,
prototype keys, invalid types and out-of-range parameters are rejected. Values
are neither coerced nor clamped. Shared PP-B01 fixtures exercise the same
contract on both sides; custom engine content must additionally pass PP-V01.
Whole-document size/depth budgets include the envelope and normalized defaults.

Create accepts only `name`, `description`, `sceneData`, `thumbnailObjectKey`, and
`playlistId`; replacement accepts only `name`, `description`, and `sceneData`.
Client fields such as `sceneMode`, `disabled`, `availability`, or owner IDs are
rejected. Operator controls remain independent and survive every content edit.

Invalid documents return `400 VALIDATION_ERROR` with a `details` map. Examples:
`sceneData.schemaVersion`, `sceneData.settings.camera.orbitSpeed`, and
`sceneData.scene.visualizer.shader`. Request-size, malformed JSON, and encoding
errors retain the PP-V01 response contract. Diagnostics never echo source.
Rejected updates leave the prior source and metadata intact.

## Stored classification and reads

The original document remains in JSONB. Migration V19 adds one server-owned
`scene_mode` column, exposed as `sceneMode` in scene and repair responses:

| Value | Meaning |
| --- | --- |
| `legacy-custom` | Existing/unvalidated content; owner repair required |
| `custom-v1` | Validated custom envelope; still untrusted source |
| `template-v1` | Validated reference to an exact platform template version |

Every pre-existing row starts as `legacy-custom`, even if its JSON already
claims to be a template. Migration does not inspect, rewrite, delete, or execute
the original source, and preserves names, ownership, thumbnails, tags and dates.
Raw entity constructors also default to legacy. The service assigns a new mode
only after full validation. A database check prevents mismatched envelope/mode
markers; it supplements, and does not replace, application validation.

Availability queries read this small column alongside operator controls without
loading shader source. Individual disablement always wins. Legacy content returns
`SCENE_UPGRADE_REQUIRED`; public responses withhold its `sceneData`. A validated
custom scene also requires both the release gate and global operator enable.
Validated templates bypass only that custom switch at the API layer. Source
publication also checks the loaded document's classification to avoid a
concurrent replacement exposing an older custom payload as a template.

The frontend in this story deliberately retains its conservative global guard.
Template selection/editing and template playback release work remain PP-B03 and
the isolation stories. A server template response does not enable that UI or
authorize arbitrary source execution.

## Owner repair and deployment order

1. Keep custom rendering disabled. Deploy the compatible frontend, which wraps
   new editor saves as custom documents, unwraps validated custom documents for
   editing, and understands unavailable/upgrade-required responses. While the
   old backend is still serving, new saves can fail validation; use a short
   write-maintenance window for the coordinated cutover.
2. Drain old backend writers, deploy this backend and run V19 on the existing
   database. Do not run old and new content writers together. Invalidate cached
   source-bearing responses and verify readiness plus availability endpoints.
3. Existing owners use `GET /api/scenes/{id}/repair` to retrieve original source
   with `playable:false`, including unsupported old data. The editor can repair
   supported raw scenes and resubmit an explicit custom envelope. Unsupported
   fields must be repaired before saving; the original remains readable for
   export. There is no bulk conversion or automatic execution.
4. A successful owner save upgrades only that scene's document/mode. It does
   not clear operator disablement or enable global custom rendering. Template
   documents stay read-only in the current editor until PP-B03.

Rollback must keep rendering and content writes disabled until both frontend and
backend understand the same document contract. Leave V19 and original documents
in place; do not strip envelopes, drop the mode column, or rewrite legacy rows
to make an old binary accept them. The database check blocks common mismatched
old-writer updates but cannot replace coordinated deployment. Take the normal
database backup before rollout; no destructive migration or reseed is required.
