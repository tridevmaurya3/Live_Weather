# Scenery S11 — Visual Scene Library + Preview Cards

Status: **SOURCE IMPLEMENTATION COMPLETE — automated Debug/tests/Release lint/R8 APK+AAB gate passed; real-device visual acceptance remains the device checkpoint**

## Goal

S11 makes scenery discovery visual instead of relying on text-only chips, while preserving the existing scenery/weather truth architecture.

## Visual scene library

The Wallpaper scenery selector now shows a horizontally scrollable visual card for:

1. Auto Scene
2. Open Sky
3. Natural Hills
4. Village
5. Farm / Crops
6. River / Lake
7. Flowers / Greenery
8. Urban / Buildings

Each card is 136dp x 152dp and contains:

- a lightweight procedural scenery thumbnail,
- the scene name,
- a `V1` / `V2` / `V3` / `V4` variation badge,
- a stronger aqua outline when selected.

## Preview rendering approach

`SceneryPreviewCardView` uses Android Canvas primitives only.

It does not use:

- downloaded images,
- bitmap assets,
- OpenGL preview surfaces,
- network calls,
- weather cache reads,
- animation timers.

Scene-specific thumbnail motifs include:

- Open Sky — clean horizon,
- Natural Hills — layered hill silhouettes,
- Village — houses + path over terrain,
- Farm / Crops — perspective crop rows + crop heads,
- River / Lake — hills + water + restrained wave lines,
- Flowers / Greenery — meadow + stems/flower points,
- Urban / Buildings — layered skyline + restrained windows.

The Auto Scene card previews the current concrete scene resolved by Auto Scene and includes an `AUTO` badge.

## Variation preview behavior

The existing direct variation chips remain 1 / 2 / 3 / 4.

Changing variation:

- updates the selected renderer variation as before,
- updates all visual library cards to show that same variation,
- updates each card's variation badge,
- preserves the existing smooth scenery transition in the actual GL scene.

This gives access to the existing 7 manual scene categories x 4 variations plus Auto without creating 28 separate heavyweight view hierarchies.

## Quick presets and favorites

S10 functionality remains intact and intentionally compact below the visual library:

- Quick Presets: Sky / Green / Water / City,
- up to three persisted scene + variation favorites,
- save current,
- one-tap restore,
- long-press favorite removal.

The large visual cards are used only for primary scene discovery so the Wallpaper page does not become excessively tall.

## Truth boundary

Preview cards are representative scenery thumbnails only.

They do not show or infer current:

- rain,
- cloud cover,
- storm,
- fog,
- snow,
- Sun/Moon obstruction,
- forecast probability.

The UI explicitly tells the user that live weather remains driven by current observations.

## Accessibility

Each preview card remains a focusable/clickable target larger than the 48dp minimum and exposes a content description containing:

- scene name,
- selected preview variation,
- total variation count.

## Performance boundary

Preview cards are static Canvas views and redraw only when normal Android UI invalidation requires it, for example:

- selection changes,
- variation changes,
- Auto's concrete resolved scene changes,
- ordinary View redraw.

There is no continuous thumbnail animation loop and no GL-frame work added by S11.

## Cloud freeze boundary

S11 does not modify `HeroGlTextureCloudRenderer.java`.

Verified cloud blob remains:

`dc3b5db66c92cdf4520b0210857426e4bca853d8`

## Source checkpoints

Visual preview card class:

`f3fca53766ddc560b16cee2b4fd306249dbae31a`

Library strings:

`bf649cd7ff09a3e3c0c426d1157baee1f500cd66`

Visual selector wiring:

`29a22e9316e8d0fed3ebaa6058a2450f50df41f5`

Android Canvas compatibility fix:

`775ac1e180740d40af1b4826031c7c13a0359422`

## Build note

The first S11 gate correctly caught one compile compatibility issue:

- unsupported `Canvas.clipRoundRect(RectF, rx, ry)` call.

It was replaced with the compatible path-based approach:

- `Path.addRoundRect(...)`,
- `Canvas.clipPath(...)`.

No feature behavior was changed by that compatibility fix.

## Automated verification

Authoritative workflow run:

`32004999957`

Passed after the compatibility fix:

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
2. Confirm the Visual scene library appears as large horizontal cards.
3. Horizontally scroll through all 8 cards.
4. Confirm each category is visually distinguishable before tapping it.
5. Tap Village/Farm/River/Flowers/Urban cards and confirm the correct actual scene is applied.
6. Change variation 1 → 2 → 3 → 4 and confirm card artwork/badge changes.
7. Select Auto Scene and confirm its card shows an AUTO badge and follows the current resolved scene identity.
8. Confirm selected card gets the stronger aqua outline.
9. Confirm Quick Presets and Favorites still work below the library.
10. Confirm page scrolling remains smooth and there is no thumbnail animation/GPU stutter.
11. Confirm actual weather remains unchanged when only preview cards are browsed.
12. Confirm cloud shape/motion remains the frozen accepted version.

## Next scenery step

After device acceptance, S12 can focus on **Scene Library UX Refinement + Full-screen Scene Preview**, allowing a larger optional preview/detail surface before applying a scenery while keeping the actual live weather pipeline authoritative.
