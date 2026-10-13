# Personal Builder templates (SB-14 / #167)

Personal templates are private, named snapshots of the Builder design: objects, groups, composition, object-level animation, bindings, frames, modifiers, materials and rendering controls. They are separate from published scenes and from the built-in catalog. Applying a template replaces the draft's Builder design while keeping its scene scale, global speed, skybox, camera, Motion, Effects and Pass Order unchanged. Scenes never store a dependency on a personal template ID. Updating or deleting one therefore does not change existing scenes, clones, or built-in templates.

## API

All routes require the current Bearer token and return `Cache-Control: private, no-store`. Other owners receive the same 404 as a missing template. There is no public template listing or document endpoint.

| Method | Route | Request / response |
| --- | --- | --- |
| GET | `/api/builder-templates` | Array of the owner's templates, sorted by name |
| GET | `/api/builder-templates/{id}` | One owned template |
| POST | `/api/builder-templates` | `{name, sceneData}` → 201 |
| PUT | `/api/builder-templates/{id}` | `{expectedRevision, name, sceneData}` → 200 |
| PATCH | `/api/builder-templates/{id}/name` | `{expectedRevision, name}` → 200 |
| DELETE | `/api/builder-templates/{id}?expectedRevision=N` | 204 |

Template responses contain `{templateId, name, sceneData, revision, createdAt, updatedAt}`. `templateId` is a stable numeric ID. Names are trimmed, 1–100 characters, and unique ignoring case within one account. Duplicate names return 409 `BUILDER_TEMPLATE_NAME_EXISTS` with `details.name`; stale revisions return 409 `BUILDER_TEMPLATE_EDIT_CONFLICT` with `details.expectedRevision`. Missing/invalid revisions return 400. The first revision is 0; both content updates and renames increment it. Delete also requires the current revision.

`sceneData` accepts only `{schemaVersion, kind: "builder", builderVersion, objects, composition?, rendering?}`. It must pass the same current Builder contract, references, transport limits, and rendering budgets as a scene. The validator supplies scene-wide defaults internally for validation, then removes them before storage and responses. Submitted `parameters` or `settings` fields are rejected, including empty or null values: this API does not save scene presets. Raw custom code, template-reference documents, unknown versions/fields, and scene-only metadata are also rejected. No shader source is executed by this API. There is no compatibility or template-version resolver.

Flyway V24 removes `parameters` and `settings` from personal templates saved during local review. It preserves their designs, IDs and names and increments the revision only where the document changes. Existing scene rows are untouched. The frontend merges an applied template design into the current scene rather than substituting a whole scene document.

## Persistence and failure behavior

Personal templates have no thumbnail, capture requirement, upload endpoint, object-storage dependency or cleanup worker. Submitted thumbnail fields are rejected. Scene thumbnails continue to use the existing scene upload API.

Template updates are atomic: invalid content, duplicate names or stale revisions leave the previous document and revision unchanged.

Flyway V25 removes the unused template thumbnail column and upload/cleanup tables created during local review. Template designs, names, IDs, revisions and timestamps stay intact. No scene columns or storage paths are changed. Previously applied migrations remain immutable.

## Local verification

Run `mvn -Dtest=BuilderTemplateIntegrationTests,BuilderSceneMigrationIntegrationTests,SceneSubmissionRequestFilterTests,SceneThumbnailUploadIntegrationTests test` with Docker available. Integration tests use real HTTP/login and a disposable PostgreSQL instance, two owners, all 14 designs, independent scene/template copies, concurrent/stale updates and invalid contracts/budgets. They verify that template responses/storage exclude scene-wide settings, thumbnail fields/routes are gone, templates never call object storage, V24/V25 preserve designs and existing scenes, and scene thumbnail uploads still work. Local startup runs the additive migrations automatically; no database or object-storage reset is needed.
