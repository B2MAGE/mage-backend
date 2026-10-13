# Personal Builder templates (SB-14 / #167)

Personal templates are private, named snapshots of the entire current Builder document. They are separate from published scenes and from the built-in catalog. Applying a template copies its document into a draft; scenes never store a dependency on a personal template ID. Updating or deleting one therefore does not change existing scenes, clones, or built-in templates.

## API

All routes require the current Bearer token and return `Cache-Control: private, no-store`. Other owners receive the same 404 as a missing template. There is no public template listing or document endpoint.

| Method | Route | Request / response |
| --- | --- | --- |
| GET | `/api/builder-templates` | Array of the owner's templates, sorted by name |
| GET | `/api/builder-templates/{id}` | One owned template |
| POST | `/api/builder-templates` | `{name, sceneData, thumbnailObjectKey?}` → 201 |
| PUT | `/api/builder-templates/{id}` | `{expectedRevision, name, sceneData, thumbnailObjectKey?}` → 200 |
| PATCH | `/api/builder-templates/{id}/name` | `{expectedRevision, name}` → 200 |
| DELETE | `/api/builder-templates/{id}?expectedRevision=N` | 204 |
| POST | `/api/builder-templates/thumbnail/presign` | `{filename, contentType, sizeBytes}` → existing presigned-upload response |
| DELETE | `/api/builder-templates/thumbnail/pending?objectKey=KEY` | Cancel an unused owned upload → 204 |

Template responses contain `{templateId, name, sceneData, thumbnailRef, revision, createdAt, updatedAt}`. `templateId` is a stable numeric ID; `thumbnailRef` is a public image URL or null. Names are trimmed, 1–100 characters, and unique ignoring case within one account. Duplicate names return 409 `BUILDER_TEMPLATE_NAME_EXISTS` with `details.name`; stale revisions return 409 `BUILDER_TEMPLATE_EDIT_CONFLICT` with `details.expectedRevision`. Missing/invalid revisions return 400. The first revision is 0; both content updates and renames increment it. Delete also requires the current revision.

`sceneData` must pass the same current Builder contract, references, transport limits, and rendering budgets as a scene. Whole document fields such as groups, frames, animation drivers, modifiers, materials, camera, motion, effects and settings are retained. Raw custom code, template-reference documents, unknown versions/fields, and scene-only metadata are rejected. No shader source is executed by this API. There is no compatibility or template-version resolver.

## Thumbnails and failure behavior

Capture a fresh preview and upload through the template-specific presign endpoint. Upload keys belong to the authenticated account, expire with the presigned URL, and can be consumed only once. Scene assets and another account's upload keys are rejected. The server validates uploaded type/size and copies the exact inspected object version into a new `scenes/builder-templates/saved/{id}/{uuid}` key. A later PUT using the original presigned URL cannot mutate a saved thumbnail.

A rename retains the image. A content update must supply a fresh upload or produces a null thumbnail, so the UI shows its fallback rather than a stale preview. Template updates are atomic: invalid content, duplicate names, stale revisions or storage-copy failures leave the previous document, revision and thumbnail unchanged. Failed captures can be omitted so naming/saving a template still works with a fallback image.

Flyway V23 creates the template table, single-use upload records and a small persistent asset cleanup queue. Obsolete saved images are queued in the same transaction as update/delete. New copies have an independently persisted provisional cleanup record before storage copy, covering rollback or an interrupted save. The cleanup worker checks retained template references before deleting, retries storage failures, and processes at most 100 entries each minute. Its interval can be set with `mage.builder-template.cleanup-delay-ms` (default 60000).

Pending/cancelled uploads are deleted five minutes after their presigned URL expires, allowing in-flight PUTs to finish and preventing a still-valid upload URL from recreating an abandoned object. Uncommitted copies are cleaned after 24 hours. These paths share the existing bucket and image limits; no separate storage service or bucket lifecycle rule is required. Do not add an expiry policy to `builder-templates/saved/`: these images stay until their template is updated/deleted. Keep the application running for queued cleanup to execute.

## Local verification

Run `mvn -Dtest=BuilderTemplateIntegrationTests,BuilderTemplateStorageTests,SceneSubmissionRequestFilterTests,SceneThumbnailUploadIntegrationTests test` with Docker available. Integration tests use real HTTP/login and a disposable PostgreSQL instance, two owners, all 14 recipes, independent scene/template copies, concurrent/stale updates, invalid contracts/budgets, single-use assets and retry cleanup. Storage tests check namespace isolation and version-conditional copies. Local startup runs V23 automatically; no database or object-storage reset is needed.
