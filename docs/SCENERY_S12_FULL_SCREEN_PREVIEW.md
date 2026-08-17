# Scenery S12 — Full-screen Scene Preview + Detail UX

Status: **SOURCE IMPLEMENTATION COMPLETE — automated Debug/tests/Release lint/R8 APK+AAB gate passed; real-device UX acceptance remains the device checkpoint**

## Goal

S12 adds a larger, non-destructive scenery inspection surface before applying a scene while preserving the fast S11 one-tap scene workflow.

## Interaction model

The Wallpaper scenery library keeps the existing behavior:

- tap a scene card → immediately select that scenery,
- long-press a scene card → open that candidate in a full-screen preview without changing the saved scenery,
- `Full preview` → open the currently selected scene in the same detail surface.

This avoids turning a visual inspection into an accidental scenery change.

## Full-screen preview

`SceneryPreviewDialog` provides:

- scene name,
- a large procedural scenery preview,
- Auto/manual status,
- scene-specific detail text,
- staged variation 1 / 2 / 3 / 4 controls,
- representative-preview truth note,
- Cancel / Close controls,
- `Use scene` or `Use Auto Scene` confirmation.

The dialog uses a full-screen window with a scrollable content surface so controls remain reachable on smaller devices and larger text configurations.

## Staged variation behavior

Variation changes inside the full-screen preview are local to the dialog.

They do not persist and do not change the active scenery until `Use scene` is pressed.

When confirmed, the existing scenery + variation persistence path is used, preserving the same App Hero / preview / Live Wallpaper runtime bridge.

## Auto Scene behavior

When Auto Scene is previewed:

- the preview artwork uses Auto's current resolved concrete scene,
- the status line names the current resolved scene,
- variation can be staged before confirming Auto,
- Auto remains a scenery-selection policy only.

The dialog does not infer or fabricate weather.

## Scene detail copy

The detail surface explains the visual purpose of each scene family:

- Open Sky — minimal foreground,
- Natural Hills — layered terrain/atmospheric depth,
- Village — restrained rural composition,
- Farm / Crops — perspective fields and current-wind-driven vegetation presentation,
- River / Lake — calm water/bank composition,
- Flowers / Greenery — meadow/grass/flower depth,
- Urban / Buildings — restrained skyline/facade depth,
- Auto Scene — current-condition/day-part scenery selection policy.

## Truth boundary

The full-screen scene preview is representative scenery artwork only.

It does not read or modify:

- current rain,
- cloud cover,
- storm,
- fog,
- snow,
- Sun/Moon state,
- alert state,
- weather cache,
- forecast probability,
- network state.

Actual weather remains authoritative in the shared live rendering pipeline.

## Performance boundary

S12 adds no additional OpenGL renderer and no continuous preview animation loop.

The large preview reuses the static Canvas-based `SceneryPreviewCardView` introduced in S11.

No bitmap downloads, network requests, weather-cache reads or GL-frame-loop work were added.

## Accessibility

S12 preserves 48dp-or-larger interactive targets and adds:

- a discoverable `Full preview` action,
- full-preview close/cancel controls,
- variation chip descriptions,
- `Use scene` content description,
- dynamic accessibility text when the staged variation changes.

## Cloud freeze boundary

S12 does not modify `HeroGlTextureCloudRenderer.java`.

Verified cloud blob remains:

`dc3b5db66c92cdf4520b0210857426e4bca853d8`

## Source checkpoints

Full-screen preview dialog:

`1f17ece28ba65943748dc65eb846af8482871af5`

Full preview strings:

`1795bce43d44b896a23ff67973206bcfc84bfd15`

Selector wiring:

`20374e83f23795379de132cd3848ac6e5c401270`

Accessibility sync/source checkpoint:

`79a15aa66c0164645a3b7748eb728e59633b458a`

## Automated verification

Authoritative GitHub Actions run:

`32006305570`

Passed:

- Gradle wrapper verification,
- Debug build,
- unit tests,
- Release lint,
- minified/R8 Release APK,
- Release AAB,
- release output verification,
- release verification artifact upload.

## Real-device acceptance checklist

1. Pull latest `main` and open Wallpaper.
2. Tap a card and confirm the existing one-tap selection still works.
3. Long-press an unselected scene card and confirm full-screen preview opens without immediately selecting it.
4. Change preview variation 1 → 2 → 3 → 4, then press Cancel and confirm active scenery/variation did not change.
5. Repeat and press `Use scene`; confirm both candidate scene and staged variation become active.
6. Select Auto Scene and use `Full preview`; confirm the status names Auto's current resolved scene.
7. Confirm Back, Close and Cancel dismiss the preview safely.
8. Check a small/narrow device or larger font size and confirm the dialog scrolls and actions remain reachable.
9. Confirm Quick Presets and Favorites remain unchanged.
10. Confirm actual current weather does not change merely from opening/browsing the scene preview.
11. Confirm Home Hero, Wallpaper preview and applied Live Wallpaper stay aligned after `Use scene`.
12. Confirm the frozen cloud renderer retains its accepted shape/motion.

## Next scenery step

After device acceptance, the next step can focus on **S13 — Scene Library Search/Grouping + Recent Scenes**, improving navigation as the scenery catalog grows without changing live weather truth.
