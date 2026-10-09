# Reproducible portfolio demo

This is the current, small demo workflow. It replaces the old 100-scene source-import
workflow for new portfolio resets. The committed `catalog.json` and `thumbnails/`
contain 14 current template scenes, four synthetic accounts, tags, conversations,
replies, votes, saves and views. There are enough scenes for discovery and owner-library
pagination. No credentials, database IDs or storage URLs are baked into the fixtures.
The thumbnail files are actual captures of these settings, reviewed October 9, 2026.

## Requirements

Docker Compose, Node 22+, Java 21 and the repository's Maven wrapper. The first run
needs network access to download pinned application dependencies and container images.
The frontend uses the matching maintained engine release. No frontend source parsing,
browser automation or legacy scene conversion is needed to seed this catalog.

## Deliberately rebuild the demo

From the backend repository in PowerShell:

```powershell
$env:MAGE_DEMO_PASSWORD = Read-Host 'Choose a password for this disposable demo'
node scripts/demo/demo.mjs reset --confirm mage-portfolio-demo
```

Use a disposable password, 12–72 characters. It is shared by the four demo accounts
and this isolated demo's database/storage services; do not use a personal password.
Passwords/tokens are never included in committed fixtures or verification reports.

The command validates all scene documents, relationships and images **before**
resetting. It identifies and replaces only the named Docker project
`mage-portfolio-demo`, its database volume and its thumbnail volume, then starts
the services, seeds through authenticated APIs and verifies the saved result.
There is no arbitrary remote reset URL. Your usual local stack and live deployment
are separate. Repeating the command recreates the same catalog, without duplicate
records or old thumbnail objects. If interrupted, rerun the explicit reset.

The API is `http://127.0.0.1:18080`; thumbnail storage is on port 19000, with its
console on 19001. Ports bind only to loopback. For a separate frontend demo session,
set `VITE_API_BASE_URL=http://127.0.0.1:18080` and retain the frontend's documented
isolated-renderer configuration. Log in as `peter.demo@example.test`,
`ari.demo@example.test`, `maya.demo@example.test` or `theo.demo@example.test` using
the password you chose. These are synthetic accounts, independent of @peter on
the user's local/live sites.

## Start without resetting; check without seeding

```powershell
node scripts/demo/demo.mjs start
node scripts/demo/demo.mjs check
node scripts/demo/demo.mjs verify
node --test scripts/demo/demo.test.mjs
```

`start` retains data and never seeds. `check` validates committed files with the
same Java validator used for saves and never touches a database. `verify` reads
the demo API/images and compares settings, ownership, tags, comments, engagement
and thumbnail hashes to the fixtures; it writes only an ignored local report.
`seed` is available for a newly empty demo started separately, and refuses to
append to a scene catalog. Neither normal application startup nor deployment
runs this workflow automatically.

## Maintaining examples

Edit the current document directly in `catalog.json`; account, discussion and
engagement links use stable fixture keys. Keep **Original** and **Selective** as
supported creative choices. Rose Reverie demonstrates Selective with a saved
bass-hit mapping; the other examples use Original. Do not convert
examples merely because Original's internal identifier is `legacy`.

After a visual change, use Create a scene to preview it and capture a fresh PNG.
Replace the matching committed thumbnail, run `check`, then reset the separate demo.
Do not reuse a thumbnail from an earlier scene revision. New Builder examples can
join this same catalog as Builder capabilities land; there is no fixed target count.

Smoke-check a bright, dark and kaleidoscope example: open, play/pause, add local
audio, adjust music controls, edit/save/reopen and confirm the thumbnail matches.
Check both supported response modes with a local song, public creator profiles,
tag filters, comments/replies and pagination. Original/Selective functional tests
belong to the frontend/engine suite; a valid PNG alone does not prove musical quality.

The checked-in scenes avoid Bloom on Prism, Spectrum and Chroma because those
templates can generate invalid color values that contaminate Bloom. This catalog
workaround is not an engine fix. All other isolation and execution limits remain.

The workflow targets a local disposable demo. To prepare a hosted demo, provision
an empty environment separately and review its configuration first; this command
does not erase or overwrite production data.
