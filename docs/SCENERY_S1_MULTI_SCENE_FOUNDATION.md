# Scenery S1 — Multi-Scene Foundation

Status: **SOURCE IMPLEMENTATION COMPLETE — automated Debug/tests/Release lint/R8 APK+AAB gate passed; real-device GLSL/visual acceptance remains the device checkpoint**

## Purpose

Scenery is a visual choice, not weather truth. The selected landscape must never create, suppress, or reinterpret rain, cloud, fog, snow, storm, Sun/Moon, temperature, or alert evidence.

## Stable scenery identities

The shared scenery model now defines these persistent modes:

- Auto
- Open Sky
- Natural Hills
- Village
- Farm / Crops
- River / Lake
- Flowers / Greenery
- Urban / Buildings

`Natural Hills` is the compatibility default so installing/pulling S1 does not intentionally switch the user's existing world style before a selector is added.

## Shared App Hero + Live Wallpaper state

`WallpaperPreferences` persists the scenery key and synchronizes it to `SceneryRuntimeState` whenever options are loaded or saved. The OpenGL world renderer reads only the process-local enum value; it does not read SharedPreferences, network data, files, or caches from the frame hot path.

## World renderer foundation

`HeroGlAnalyticWorldRenderer` now contains low-cost channels for:

- restrained open-sky foreground,
- the existing natural-hills baseline,
- generic village / urban settlement silhouettes,
- perspective farm rows,
- river/lake water and weather-aware reflections,
- flowers/greenery meadow material.

These are foundation channels only. Rich scene composition and user-facing selection are intentionally deferred to the next scenery step.

## Weather invariants

- Rain/drizzle wetness remains current-precipitation gated.
- Fog/haze continues to attenuate distant world layers.
- Sun/Moon reflections remain visibility/light driven.
- Storm darkness remains current-storm driven.
- Scenery selection never fabricates weather.

## Performance invariants

- no network work in the frame loop,
- no SharedPreferences/disk reads in the frame loop,
- no per-frame Java object allocation added by scenery selection,
- scene choice reaches the shader as one stable float id.

## Cloud freeze boundary

The active cloud renderer remains the restored stable blob `dc3b5db66c92cdf4520b0210857426e4bca853d8`. S1 does not modify cloud shape, cloud atlas logic, or cloud motion.

## Verification

Authoritative source gate: GitHub Actions run `31995321279` on source commit `67d18ab1c60a6422376a433a1c9a9a129a5a5d5c`.

Passed steps:

- Debug build,
- unit tests,
- Release lint,
- minified/R8 Release APK,
- Release AAB,
- release output verification and artifact upload.

GLSL programs are still compiled by the device OpenGL driver at runtime, so real-device rendering remains the visual/GPU acceptance boundary.

## Next step

S2 should add a user-facing scenery selector and turn the first selected packs into richer cinematic scenes while preserving the same shared weather pipeline.
