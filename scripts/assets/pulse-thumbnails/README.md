# Pulse demo thumbnails

These five PNGs render the supplied discovery mockup's original organic-ring artwork and colored gradient backgrounds. They replace the earlier procedural flower-shaped demo images, not user-captured thumbnails.

`source.html` preserves the mockup's ring geometry, colors, shadows, and resting-frame transforms. Each tile is 320 by 200 CSS pixels and is captured at 2.5x resolution (800 by 500 PNG). The surrounding application still owns card borders, corners, hover controls, and layout.

The palette order is violet, blue, warm rose, green, lavender. The local seed cycles through these five assets.

To regenerate, run `node scripts/render-pulse-thumbnails.mjs` with Playwright available. Alternatively, set `MAGE_PLAYWRIGHT_MODULE` to the absolute path of an existing Playwright ES module entry point. Playwright is only needed for artwork regeneration, not seeding or running the app.
