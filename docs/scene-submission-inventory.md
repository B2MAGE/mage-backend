# Read-only scene submission inventory

Use this inventory before rolling out or changing [submission limits](scene-submission-limits.md). It calls the same `SceneSubmissionValidator` as create/update operations. It does not start Spring, connect to a database, rewrite scenes, compile shaders, render scenes, or evaluate source. Passing this check establishes bounded data, not safe executable source.

## Run an inventory

Requires Node.js and Java 21. The first command compiles the Java tool using the repository's Maven wrapper and resolves its dependencies. It may need network access to the configured Maven repository. Later invocations can use `--no-build` when the Java classes and classpath still match the current checkout.

```powershell
node scripts/audit-scene-submissions.mjs path/to/scene-export.json
node scripts/audit-scene-submissions.mjs --no-build path/to/scene-export.json
```

Input is a UTF-8 JSON array. Each record requires `sceneData`; identifiers may use `sceneId` or `id`. API list responses already have this shape. Prefer exports containing only the identifier and scene data; account details and authentication tokens are unnecessary.

```json
[
  {
    "sceneId": 42,
    "sceneData": {
      "visualizer": { "shader": "sphere(1);" }
    }
  }
]
```

The command emits a JSON summary with valid/invalid counts, maximum serialized scene and shader UTF-8 byte lengths, container depth, enabled effect count, top-level field names, and identifiers/field paths for invalid records. `outputPass` is not counted as an effect. Disabled entries in `fx.passOrder` do not consume the enabled-effect budget. Source text, scene titles, owners, and raw parser errors are never included in the report. Failure details stop after 100 records; the total invalid count and omitted-detail count remain available. Identifiers, field paths, and top-level field names are bounded.

Exit status is `0` when all records pass, `1` when records violate the submission policy, and `2` for an unusable export or a tool/build failure. Exports are capped at 64 MiB before tree parsing and 10,000 records; duplicate JSON keys and trailing JSON documents are rejected. Parser nesting is capped at 128 to allow reports on scenes that exceed the much smaller submission-depth limit. Split larger exports into batches. This offline export limit is not the HTTP request-body limit.

Existing scenes are not migrated, clamped, or deleted. Review reported identifiers before choosing an explicit edit or a later migration. A failed record does not block inspection of the remaining well-formed records.

## Corpus used to choose the initial limits

The checked-in data snapshots under `src/test/resources/scene-corpus/` are regression inputs, not a backend template renderer or a source registry:

| Corpus | Scenes | Largest scene JSON | Largest shader | Maximum container depth | Maximum enabled effects, excluding output |
| --- | ---: | ---: | ---: | ---: | ---: |
| Supported version-one preset payloads | 14 | 5,200 bytes | 3,871 bytes | 4 | 0 |
| All quality-review demo variants | 100 | 3,351 bytes | 2,090 bytes | 4 | 1 |
| Local API inventory observed 2026-10-02 | 21 | 13,082 bytes | 11,622 bytes | 4 | 2 |

All 114 repository snapshots and all 21 observed local scenes passed the final Java validator without changes. Byte figures above use the Java inventory's compact serialization, which is also used for the stored-scene byte limit. The local observation was a read-only `GET /api/scenes`; its user scene data is deliberately not checked in. The repository snapshots include all 14 supported preset shaders and all 100 variations across the ten quality-review families. `provenance.json` records the frontend commit, source paths, fixture checksums, scene identifiers, and shader fingerprints. File checksums use UTF-8 with LF newlines. Tests recheck these fingerprints and validate all 114 snapshots against the actual Java validator.

The 64 KiB source and 256 KiB scene budgets leave headroom above these samples. This is evidence for the initial policy, not a complete production inventory or a performance guarantee. Separately, inspection of the platform-owned version-one presets found a highest authored raymarch iteration setting of 198. The runtime policy therefore uses 200 rather than the original candidate 128. This inventory never scans submitted source for iteration calls; actual runtime enforcement belongs to PP-V02. Passing data validation cannot prove how long arbitrary source will execute or how much GPU work it performs.

## Seed and import preflight

`seed-quality-local.mjs` and `seed-pulse-local.mjs` call the exported `validateSceneSubmissions(records)` helper before registering accounts, uploading thumbnails, or creating scenes. They now require Java 21 and Maven dependencies in addition to their existing Node/Docker/frontend prerequisites. The entire planned batch is checked once using the current checkout's Java validator, even when the local API happens to run an older build. The API still validates each write independently.

The helper stores its batch briefly in a uniquely named ignored `.local/scene-preflight-<uuid>.json`, runs the offline validator, and removes that exact temporary file in `finally`. It returns the summary on success and throws before any seed mutation on failure. Importing the module has no CLI or build side effects. Future import scripts should use the same helper before starting mutations; direct database writes bypass the application's protections and are not supported import paths.

```javascript
import { validateSceneSubmissions } from './audit-scene-submissions.mjs';

await validateSceneSubmissions(plannedScenes.map(scene => ({
  sceneId: scene.id,
  sceneData: scene.data,
})));
// Only begin the import's writes after this succeeds.
```

The quality seeder's existing `--validate-only` option remains read-only, but still requires its matching captured thumbnails, render-validation report, and dedicated review containers. The inventory itself needs none of those assets or containers.

## Refreshing the test snapshots

This maintainer command requires a frontend checkout with its normal TypeScript dependency installed:

```powershell
node scripts/build-scene-submission-fixtures.mjs ../mage-frontend-audio-response
```

It uses the TypeScript syntax tree to read literal template IDs and shader strings, verifies the platform catalog's shader checksums, and combines those strings with the documented PP-B01 version-one engine defaults. It does not import or execute the editor, template source, or shaders. The separate platform-owned quality catalog builder generates its fixed demo data without evaluating shader strings. Review changes to the generator's version-one defaults against the frontend resolver before refreshing a snapshot. Do not regenerate immutable template versions just to make a failing limit test pass.

Run `SceneSubmissionInventoryTests` with the backend test suite after a refresh. Operator audits do not need a frontend checkout or TypeScript: the Java validator and JSON exports are sufficient.
