# Pulse mockup review environment

This local-only setup uses a dedicated PostgreSQL volume and fresh backend/database containers. The original database volume remains intact. MinIO uses the repaired community image and does not require an AIStor license.

Prerequisites: Docker Desktop running, the backend `.env` configured for local development, Node 22+, and the sibling `mage-frontend` checkout with its dependencies installed. The seed imports that checkout's actual scene defaults, sanitizer, and bundled shaders.

From this repository in PowerShell:

```powershell
./scripts/start-pulse-local.ps1
```

This includes the base, local, MinIO, and Pulse Compose files in that order, starts the services, waits for the API, and seeds through the real API only when the database has no scenes. Normal restarts preserve all existing review data, including newly created scenes and comments. The first community MinIO image build takes several minutes if it is not already present. The frontend remains the normal Vite development server at http://127.0.0.1:5173.

To create another fresh database while preserving every previous volume:

```powershell
./scripts/start-pulse-local.ps1 -DatabaseVolume mage-pulse-review-v2
```

Use that same argument on subsequent starts to keep using the chosen volume. The default is `mage-pulse-review-postgres-v1`. Do not use `down -v` to reset it.

To rerun the seed against the current review database without rebuilding containers:

```powershell
node scripts/seed-pulse-local.mjs
```

The seed reuses demo accounts and scenes, avoids duplicate comments/replies, and does not inflate views on a rerun. It refuses non-loopback API targets and databases containing other scene names. Use it only with the dedicated review database.

## Review data

- 12 local accounts, 30 renderable scenes, tags, votes, saves, and 92 comments/replies.
- Ari Rivera owns 14 scenes, enough for reviewing My Scenes pagination.
- Every scene has an actual PNG in MinIO, uploaded using the signed upload API and verified by a public GET. Thumbnails reuse the mockup's ring artwork and five colored backgrounds; playback uses real engine shaders.
- Featured: **Neon Bloom**, by **Ari Rivera**, ID **1** on a fresh database. Its description and tags follow the home mockup.
- Set `VITE_HOME_FEATURED_SCENE_ID=1` in the frontend's local environment. The manifest records the actual ID if resuming.

Sign in with `ari@pulse.local` and `PulseDemo2026!`. Every demo account shares that local-only password. Account names, scene IDs, and validation results are written to `.local/pulse-seed-manifest.json`, excluded from Git. Access tokens are never saved.

The seed exercises registration, login, scene creation, thumbnail upload/finalization, tags, comments/replies, scene/comment voting, saves, views, and anonymous thumbnail reads. No fixtures are added to production migrations.

## Refresh existing demo thumbnails

The five PNGs in `scripts/assets/pulse-thumbnails/` are rendered from the discovery mockup's own ring artwork and colored backgrounds. Fresh seeds cycle through those assets; scene playback still uses the real shaders.

To preview replacing only the old generated demo images in the current review database:

```powershell
node scripts/update-pulse-thumbnails.mjs
```

To apply that preview:

```powershell
node scripts/update-pulse-thumbnails.mjs --apply
```

The updater requires the original `.local/pulse-seed-manifest.json` and its loopback API target. It checks the recorded scene ID, owner ID, original thumbnail URL, and an exact SHA-256 match against the original generated PNG before replacing anything. Newly created scenes and changed/custom thumbnails are skipped, including images replaced at the same URL. It changes only thumbnail references through each recorded owner's normal upload/finalization APIs; accounts, scene settings, names, descriptions, tags, comments, votes, saves, and views are not rewritten.

Dry run performs only reads and prints a report. Before applying, every eligible original PNG and the original seed manifest are backed up under `.local/pulse-thumbnail-backups/<timestamp>/`; `report.json` records original/replacement references, hashes, object keys, and each scene's progress. The backend deletes the old storage object when it finalizes a replacement, so retain those local backups if rollback may be needed. To recover a thumbnail, sign in as its recorded demo owner and upload the backed-up `<sceneId>-original.png` through that scene's thumbnail presign/PUT/finalize flow; the backup report identifies the scene and owner. Do not rerun the full seed to restore an image.

The original manifest is left unchanged. Rerunning the updater skips references already replaced and can safely finish remaining originals after an interrupted run. A failed run stops immediately and prints its recovery report path; inspect any `uploading` or `finalizing` entries before resuming. Avoid editing demo thumbnails while an update is in progress.
