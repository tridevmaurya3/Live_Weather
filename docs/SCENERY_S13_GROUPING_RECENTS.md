# S13 — Scene Library Grouping + Recent Scenes

## Goal

Keep the growing Live Wallpaper scenery catalog fast to browse without changing weather truth, the live rendering pipeline, or the frozen cloud renderer.

## What changed

### Grouped visual scene library

The existing S11/S12 preview cards are now organized into three compact sections:

- **Sky & smart** — Auto Scene, Open Sky
- **Nature** — Natural Hills, Farm / Crops, River / Lake, Flowers / Greenery
- **Places** — Village, Urban / Buildings

The visual cards preserve the established behavior:

- tap = select the scene immediately
- long press = open the S12 full-screen non-destructive preview
- selected card = highlighted
- Variation 1 / 2 / 3 / 4 remains shared across the library

### Recent scenes

A new persistent **Recent scenes** row remembers up to five manual `scene + variation` combinations.

Behavior:

- newest used combination appears first
- selecting the same scene + variation again moves it to the front instead of duplicating it
- the list is capped at five combinations
- tapping a recent chip restores its exact scene and variation
- long pressing a recent chip opens the full-screen S12 preview at that stored variation
- preview remains non-destructive until **Use scene** is pressed
- Auto Scene is deliberately not stored as a fixed recent shortcut because its resolved scenery may change with current observed conditions and day-part

Recent data is stored only in `live_weather_wallpaper_preferences` by `SceneryRecentPreferences`. It contains scenery presentation identity and variation only; it does not contain weather, location, condition, rain, cloud, astronomy, or forecast state.

## Preserved behavior

S13 does not remove or replace the existing systems:

- Quick Presets remain available
- up to 3 Favorites remain available
- Favorite save / restore / long-press remove behavior remains unchanged
- Auto Scene intelligence remains unchanged
- S12 full-screen preview behavior remains unchanged
- weather truth remains controlled by the existing weather / astronomy pipeline
- no SharedPreferences access was added to the GL draw-frame path

## Cloud freeze guard

`HeroGlTextureCloudRenderer.java` is intentionally untouched in S13. The frozen blob remains:

`dc3b5db66c92cdf4520b0210857426e4bca853d8`

## Real-device acceptance

After pulling `main`, open **Wallpaper** and verify:

1. Visual scene library shows **Sky & smart**, **Nature**, and **Places** groups.
2. Select several manual scenes and different variations.
3. **Recent scenes** appears and keeps the newest combination first.
4. Re-select the exact same scene + variation and confirm it moves to the front without a duplicate.
5. Use more than five different combinations and confirm only the newest five remain.
6. Tap a recent entry and confirm both scene and variation restore together.
7. Long press a recent entry, change preview variation, then Cancel; active selection must remain unchanged.
8. Repeat and press **Use scene**; only then should the previewed scene + variation become active.
9. Select Auto Scene and confirm Auto is not added as a misleading fixed recent shortcut.
10. Re-test Quick Presets, Favorites, and full-screen preview.
11. Confirm cloud visuals are unchanged from the frozen baseline.

## S13 status

Source implementation complete. Final status depends on the repository release gate and real-device acceptance.
