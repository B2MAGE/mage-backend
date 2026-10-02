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
- Every scene has an actual PNG in MinIO, uploaded using the signed upload API and verified by a public GET. Thumbnails are deterministic fixture artwork; playback uses real engine shaders.
- Featured: **Neon Bloom**, by **Ari Rivera**, ID **1** on a fresh database. Its description and tags follow the home mockup.
- Set `VITE_HOME_FEATURED_SCENE_ID=1` in the frontend's local environment. The manifest records the actual ID if resuming.

Sign in with `ari@pulse.local` and `PulseDemo2026!`. Every demo account shares that local-only password. Account names, scene IDs, and validation results are written to `.local/pulse-seed-manifest.json`, excluded from Git. Access tokens are never saved.

The seed exercises registration, login, scene creation, thumbnail upload/finalization, tags, comments/replies, scene/comment voting, saves, views, and anonymous thumbnail reads. No fixtures are added to production migrations.

