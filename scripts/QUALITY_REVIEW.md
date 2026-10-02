# Local scene quality review

This workflow creates ten accounts, ten scenes per account, captured thumbnails,
and conversations with varied engagement and publication dates over the past year.
It is restricted to the local API on port 8080 and the named PostgreSQL volume
`mage-pulse-review-postgres-v2-quality`. It does not push, merge, or deploy anything.
Generated captures, reports, checkpoints, and SQL stay in ignored `.local/quality-review/`.

## Start the fresh review database

In the backend's local `.env`, set:

```dotenv
MAGE_PULSE_DATABASE_VOLUME=mage-pulse-review-postgres-v2-quality
```

From `mage-backend`, start the stack with its existing local storage configuration:

```powershell
docker compose -f docker-compose.yml -f docker-compose.local.yml -f docker-compose.minio.yml -f docker-compose.pulse.yml up --build --detach
```

Switching this named volume preserves `mage-pulse-review-postgres-v1` and the
original `postgres_data` volume. Do not use `down -v` or delete either old volume.
Do not use `start-pulse-local.ps1` for this workflow: it automatically runs the old
30-scene seed when the database is empty. The quality seeder requires a fresh
database and never resets one itself.

## Capture and review real scene thumbnails

Run the frontend development server on `127.0.0.1:5173`. These scripts require a
Node.js version with the global `fetch` and `WebSocket` APIs (Node.js 22 or later).
From `mage-backend`, start a separate headless Edge profile for the capture harness:

```powershell
$reviewBrowserProfile = Join-Path (Get-Location) '.local/quality-review/browser'
$reviewBrowserArguments = @(
    '--headless=new', '--disable-gpu-sandbox', '--disable-sync',
    '--no-first-run', '--no-default-browser-check', '--remote-allow-origins=*',
    '--remote-debugging-address=127.0.0.1', '--remote-debugging-port=9225',
    '--autoplay-policy=no-user-gesture-required', '--mute-audio',
    ('--user-data-dir="' + $reviewBrowserProfile + '"'),
    'http://127.0.0.1:5173/scripts/quality-scene-capture.html'
)
Start-Process -FilePath 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe' -ArgumentList $reviewBrowserArguments -WindowStyle Hidden
```

The optional sample checks one example from each of the ten shape families. After
reviewing it, run the full capture. The full run is required before seeding:

```powershell
node scripts/capture-quality-scenes.mjs --sample
node scripts/capture-quality-scenes.mjs
powershell -NoProfile -File scripts/review-quality-thumbnails.ps1
```

Inspect `.local/quality-review/contact-sheet-1.png` through `contact-sheet-4.png`
and individual captures in `thumbnails/`. `render-validation.json` records sampled
render phases and audio-response checks. The seeder requires 100 distinct passing
results, no browser errors, and an exact fingerprint match to the current catalog.
If the catalog changes, repeat the full capture; a partial or stale report fails
preflight before any accounts are registered.

## Seed and verify

```powershell
node scripts/seed-quality-local.mjs --validate-only
node scripts/seed-quality-local.mjs
```

The first command performs read-only checks. The second registers the accounts,
uploads and verifies the captured PNGs, creates scenes and comments through the
app's APIs, and applies historical dates and engagement in a local transaction.
Votes and saves reference the same ten accounts; views include anonymous visits.
It checks the results through both the database and the public API.

If an execution stops partway through, leave the content, catalog, captures, and
volume in place, then resume with:

```powershell
node scripts/seed-quality-local.mjs --resume --validate-only
node scripts/seed-quality-local.mjs --resume
```

The checkpoint must match the volume, content, and captures. Resume reuses created
records and fills missing engagement without doubling view counts. Completed runs
do not seed again. Access tokens are kept in memory and never written to reports.

`seed-manifest.json` contains user/scene IDs, captured-thumbnail references, dates,
engagement, and validation totals. It also identifies the homepage feature through
`VITE_HOME_FEATURED_SCENE_ID`; set that value in the frontend's local environment
and restart its development server if necessary. The first featured candidate is
**A Place to Land**. A local login is `ari@pulse.local` / `PulseDemo2026!`; the other
nine accounts use the same local-only password.
