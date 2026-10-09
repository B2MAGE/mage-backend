# Current scene documents

`POST /api/scenes` and `PUT /api/scenes/{id}` require an explicit version-one
document in `sceneData`. Bare engine objects are no longer accepted for writes.
`SceneDocumentValidator` applies the checked-in PP-B01 schema/catalog, fills
documented template defaults, and reuses PP-V01 resource limits before any
entity mutation, thumbnail finalization, or playlist attachment.

## Write contract

Custom source remains untrusted, regardless of creator role or shader similarity:

```json
{"name":"My scene","tagIds":[],"sceneData":{"schemaVersion":1,"kind":"custom","scene":{"visualizer":{"shader":"sphere(0.5);"}}}}
```

Templates refer only to the 14 supported catalog entries; source is not accepted
in a template document:

```json
{"name":"My template","tagIds":[],"sceneData":{"schemaVersion":1,"kind":"template","templateId":"embedded-scene-0","templateVersion":1,"parameters":{"scale":10,"speed":1}}}
```

The exact schema and catalog live in `src/main/resources/contracts/scenes/`.
Unknown versions, template IDs, root/nested fields, mixed template/source data,
prototype keys, invalid types and out-of-range parameters are rejected. Values
are neither coerced nor clamped. Shared PP-B01 fixtures exercise the same
contract on both sides; custom engine content must additionally pass PP-V01.
Whole-document size/depth budgets include the envelope and normalized defaults.

PP-B03 extends template settings with bounded controls for camera position/orientation, motion,
initial state, music-response mappings, built-in effects and effect order. Existing camera,
bloom, tint and parameter fields remain canonical. The new fields are optional, and absent
fields keep the original version-one defaults; no shader catalog or database migration changes.
Templates remain `template-v1` after editing these settings. Shader source is still rejected
at every template level. The schema extensions `x-uniqueBy` and `x-maxOptionalEffects` enforce
unique audio mapping targets and the shared four-effect budget, including bloom and tint.
Deploy this backend before the frontend exposes the expanded controls; the old validator
rejects new fields, and older frontends must refresh before editing expanded documents.

SB-04 completes API support for the Builder fields already exposed by the editor:
ordered Expand/Shell/Twist modifiers, up to two nested Line/Ring arrangement stages,
and bounded per-object spin. Array order and authored values survive create,
replace and reopen. The API counts expanded copies and their modifier, animation,
transform and material costs before persistence; two arrangements with counts 2
and 8 use the entire 16-primitive budget even though the document has one object.
See [the Builder contract](../src/main/resources/contracts/scenes/builder-v1.md)
and the checked-in rendering policy for exact bounds. No database migration or
new audio behavior is part of this change. Deploy the API support before relying
on the editor's expanded documents being saved successfully.

Create accepts only `name`, `description`, `sceneData`, `thumbnailObjectKey`,
`playlistId`, and `tagIds`; replacement accepts only `name`, `description`,
`sceneData`, and `tagIds`. Both require the complete `tagIds` array; `[]` clears
the selection. The scene and validated tag assignments commit atomically,
including rollback when a database write fails after the scene is flushed.
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
| `legacy-custom` | Existing/unvalidated content; unsupported and withheld |
| `custom-v1` | Validated custom envelope; still untrusted source |
| `template-v1` | Validated reference to an exact platform template version |
| `builder-v1` | Validated editable document using the trusted Builder compiler |

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

## Owner editing and coordinated deployment

`GET /api/scenes/{id}/repair` remains an authenticated owner-only read for current
supported documents, including scenes blocked from playback by operator controls.
It returns `playable:false`; obtaining editable source does not authorize playback.
Historical raw documents, retired templates, and documents rejected by the current
schema return `409 SCENE_DOCUMENT_UNSUPPORTED` without source. No conversion or
upgrade adapter runs on read or write. Owners may replace a scene with a complete,
valid current document; operator disablement stays in place.

Original music response keeps its `legacy` wire identifier and Selective keeps
`mapped-v1`. The abandoned `transient-v1` response and `reaction-rings-v1` /
`reaction-lantern-v1` templates are rejected. These are schema restrictions;
existing JSONB rows are neither rewritten nor deleted.

Deploy frontend and backend contracts together, including required `tagIds` on
scene writes. Keep custom rendering disabled until the isolation release checks
and operator enable are complete. Retain existing database migrations and stored
rows; rollback must use a matching frontend/backend contract rather than stripping
envelopes or rewriting historical data.
