# S13 — Scene Library Grouping + Recent Scenes

Status: **COMPLETE — source implementation, automated release gate and user real-device acceptance passed on 2026-08-17.**

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

## Automated verification

Authoritative S13 source release-gate run:

- GitHub Actions run: `32007014547`
- source head: `1223328a014a83d41674407140bc405081884ec7`
- result: **SUCCESS**
- artifact: `phase-26-release-verification`
- artifact ID: `9280456673`
- artifact digest: `sha256:1f4202838e91728a64c831fb5817faff554af26a5db5d7c8c2954cada95bc40d`

Passed:

- Gradle wrapper verification
- Debug build
- unit tests
- Release lint
- Release APK / AAB
- R8 / minification gate
- release-output verification
- release verification artifact upload

## Real-device acceptance

Accepted by the user on **2026-08-17** after pulling `main` and testing the S13 Wallpaper experience.

Acceptance covered the requested checkpoint set:

1. Visual scene library groups: **Sky & smart**, **Nature**, **Places**.
2. Multiple manual scenes and variations.
3. Recent scenes newest-first behavior.
4. Duplicate prevention / MRU promotion.
5. Five-entry cap.
6. Exact scene + variation restore.
7. Long-press preview + Cancel non-destructive behavior.
8. Long-press preview + Use scene apply behavior.
9. Auto Scene excluded from misleading fixed recents.
10. Quick Presets, Favorites and full-screen preview regression.
11. Frozen cloud visual regression.

## S13 status

**COMPLETE.** No separate S14 scenery step is documented in the repository. Further work returns to the authoritative Phase 26 consolidated real-device acceptance plan.
