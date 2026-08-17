# Scenery S4 — Rich Variations Library

Status: **SOURCE IMPLEMENTATION COMPLETE — automated Debug/tests/Release lint/R8 APK+AAB gate passed; real-device GLSL/visual acceptance remains the device checkpoint**

## What S4 adds

S4 expands the seven user-facing scenery categories with four stable, user-controlled composition variants per category.

That creates **28 selectable visual combinations** across:

- Open Sky
- Natural Hills
- Village
- Farm / Crops
- River / Lake
- Flowers / Greenery
- Urban / Buildings

Variation changes are deliberate user choices. The app does not randomly rotate scenery in the background.

## Persistent variation state

`SceneryVariantRuntimeState` stores one bounded variation id (`0..3`) in the existing `live_weather_wallpaper_preferences` store under `scenery_variant`.

`LiveWeatherApplication` initializes that state once when the process starts. The OpenGL world renderer then uses only the process-local integer; no preference or disk access occurs in the frame hot path.

## Wallpaper selector

`ScenerySelectorView` now includes a compact 48dp minimum-touch `Change variation` control beneath the seven scene chips.

The selector shows:

- current scene,
- current variation number (`1 of 4` through `4 of 4`),
- accessibility description and selection announcements.

The selected variation is persistent and shared by the in-app Hero, Wallpaper preview and Android system Live Wallpaper.

## Smooth variation morph

Scene changes and variation changes share the same approximately 1.8 second eased transition path.

The renderer keeps:

- scenery from/to,
- variation from/to,
- one smooth transition mix.

This avoids a hard jump or blank frame when the user changes only the variation.

## Variant behavior

**Variation 1** preserves the S3 visual baseline.

Variations 2-4 change presentation geometry and material rhythm only:

### Open Sky

- subtle foreground profile changes,
- slightly different horizon/ground framing while preserving maximum sky area.

### Natural Hills

- different ridge phase/frequency profiles,
- subtle terrain height and vegetation rhythm changes,
- restrained palette variation.

### Village

- different generic house spacing and density,
- roof-center / roof-slope variation,
- changed curved-path rhythm,
- restrained night-window distribution.

### Farm / Crops

- different primary and fine crop-row spacing,
- perspective rhythm changes,
- restrained crop/field tone variation,
- current wind remains the only driver of crop sway.

### River / Lake

- slightly different bank position/profile,
- multiple stable wave-frequency combinations,
- restrained water-tone variation,
- Sun/Moon/weather-driven reflections remain authoritative.

### Flowers / Greenery

- different meadow texture density,
- different restrained flower distributions and palette mixes,
- current wind remains the only motion driver.

### Urban / Buildings

- different generic building spacing/density,
- varied height rhythm and footprint,
- facade split and restrained night-window variation,
- no real landmark is fabricated.

## Weather-truth invariants

Scenery variation is presentation-only.

- current rain/drizzle alone controls wetness,
- current fog/haze controls distance attenuation,
- current storm controls storm darkness,
- current Sun/Moon visibility and scene light control reflections,
- current wind strength alone drives vegetation/crop sway,
- changing variation cannot create or suppress weather,
- forecast probability is never converted into current rain/storm visuals.

## Performance invariants

- no scenery network request,
- no SharedPreferences/disk read in the GL frame hot path,
- no new per-frame Java object allocation for variation selection,
- renderer reads one process-local integer plus existing enum state,
- existing adaptive FPS/detail governor is unchanged.

## Cloud freeze boundary

S4 does not modify `HeroGlTextureCloudRenderer.java`.

Verified active cloud blob:

`dc3b5db66c92cdf4520b0210857426e4bca853d8`

## Automated verification

Authoritative source gate:

- source commit: `0dc12019978accdf29b61cb9f9c5a1fc4703623f`
- GitHub Actions run: `31997419326`

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

GLSL programs are compiled by the phone GPU driver at runtime, so real-device visual acceptance remains required.

1. Pull `main` and open the Wallpaper page.
2. Confirm `Change variation` is visible below the scene chips.
3. Select Natural Hills and cycle Variation 1 -> 2 -> 3 -> 4.
4. Confirm each variation morphs smoothly without black/blank frames.
5. Repeat the four variations for Village, Farm, River/Lake, Flowers/Greenery and Urban.
6. Confirm each category remains recognizably the same category while the composition changes.
7. Confirm Open Sky variants keep the sky dominant.
8. Restart the app and confirm the chosen variation is retained.
9. Confirm Home Hero uses the same scene + variation.
10. Apply/open the Android Live Wallpaper and confirm the same scene + variation is used.
11. Change only the variation and confirm rain/cloud/fog/storm truth does not change.
12. Confirm crop/flower motion still follows current wind only.
13. Confirm cloud shape/motion remains unchanged between variations.
14. Check `LiveWeatherGL` logs for world-shader compile/link errors.

## Next scenery step

After S4 receives real-device GPU/visual acceptance, S5 can add restrained scene-specific natural micro-details while preserving weather truth, performance limits and the cloud freeze boundary.
