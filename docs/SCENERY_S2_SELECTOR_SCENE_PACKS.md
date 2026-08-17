# Scenery S2 — Selector + Selectable Scene Packs

Status: **SOURCE IMPLEMENTATION COMPLETE — automated Debug/tests/Release lint/R8 APK+AAB gate passed; real-device GLSL/visual acceptance remains the device checkpoint**

## What S2 adds

The Wallpaper page now exposes a compact persistent scenery selector for these real selectable visual packs:

- Open Sky
- Natural Hills
- Village
- Farm / Crops
- River / Lake
- Flowers / Greenery
- Urban / Buildings

`Auto` remains a stable internal scenery identity from S1 but is intentionally not exposed as a user-facing smart mode yet, because no automatic scenery-selection policy has been defined. This avoids presenting unfinished behavior as a working feature.

## Selector behavior

`ScenerySelectorView` is a self-contained, lifecycle-safe selector embedded in `screen_wallpaper.xml`.

- 48dp minimum touch targets
- compact horizontally scrollable chips
- selected-state visual highlight
- persisted selected scenery summary
- accessibility content descriptions and selection announcements
- no MainActivity scene-selection listener required

Selecting a scene updates `WallpaperPreferences` and the process-local `SceneryRuntimeState`. The shared world renderer reads that stable enum directly, so the in-app Hero, Wallpaper preview and active Android Live Wallpaper use the same scenery identity.

## Preference safety fix

The legacy seven-argument `WallpaperPreferences.Options` constructor now preserves `SceneryRuntimeState.get()` instead of forcing `Natural Hills`.

This is important because the existing rain, cloud, lightning, snow, fog, stars and battery switches still create `Options` through that compatibility constructor. Toggling any of those effects therefore no longer resets the user's scenery selection.

The Live Wallpaper service option identity now also includes `getSceneryMode()`, keeping its applied-options cache semantically complete.

## Scene-pack differentiation

`HeroGlAnalyticWorldRenderer` keeps Natural Hills as the compatibility baseline and strengthens only alternate packs:

- **Open Sky** — restrained near-ground silhouette, leaving maximum sky area visible.
- **Village** — low generic structures with simple roof silhouettes and restrained night windows.
- **Farm / Crops** — field base plus perspective crop rows.
- **River / Lake** — bank transition, animated water surface and current-light/current-precipitation reflections.
- **Flowers / Greenery** — denser meadow material with restrained flower highlights.
- **Urban / Buildings** — taller varied generic structures with restrained night windows.

No location-specific landmark is invented.

## Weather-truth invariants

Scenery remains presentation-only.

- current rain/drizzle alone controls wetness,
- fog/haze attenuates world layers,
- current storm controls storm darkness,
- Sun/Moon visibility and scene light control reflections,
- scenery cannot create or suppress a weather condition,
- forecast probability is never used to fabricate current rain/storm visuals.

## Performance invariants

- no scenery network request,
- no SharedPreferences/disk read in the GL frame hot path,
- no new per-frame Java allocation for scenery selection,
- one stable scenery shader id is read from process-local state,
- existing adaptive frame/detail governor remains unchanged.

## Cloud freeze boundary

S2 does not modify the cloud engine. The active `HeroGlTextureCloudRenderer.java` blob remains:

`dc3b5db66c92cdf4520b0210857426e4bca853d8`

Cloud atlas, cloud movement and cloud weather-truth behavior are outside this scenery step.

## Automated verification

Authoritative source gate:

- source commit: `cc56b668ff685acc41af14e8cef2b5fd2999b5a4`
- GitHub Actions run: `31996081310`

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

GLSL is compiled by the phone GPU driver at runtime, so real-device acceptance is still required.

1. Open Wallpaper page and confirm the selector is horizontally scrollable.
2. Select each of the seven scenes and confirm the selected chip/summary updates.
3. Confirm the preview changes without reloading weather.
4. Navigate to Home and confirm the same selected scenery is used there.
5. Apply/open the Android Live Wallpaper and confirm the same scenery is used.
6. Toggle Rain/Clouds/Lightning/Snow/Fog/Stars/Battery and confirm the selected scenery does not reset.
7. Select Village and Urban in daylight and confirm structures are clearly present but not oversized.
8. Select Farm and confirm crop-row perspective is visible.
9. Select River/Lake and confirm water reacts to scene light/rain without mirror-like reflection.
10. Select Flowers/Greenery and confirm meadow/flower accents remain restrained.
11. Select Natural Hills and confirm the established baseline look remains intact.
12. Confirm clouds retain the previously accepted shape/motion and do not change between scenery modes.
13. Check `LiveWeatherGL` logs for any world-shader compile/link failure.

## Next scenery step

S3 can add deeper cinematic world detail and optional scene-transition polish after S2 receives real-device GPU/visual acceptance. It must continue to preserve the cloud freeze boundary and weather-truth invariants.
