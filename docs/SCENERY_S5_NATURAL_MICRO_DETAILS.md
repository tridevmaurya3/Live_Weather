# Scenery S5 — Scene-specific Natural Micro Details

Status: **SOURCE IMPLEMENTATION COMPLETE — automated Debug/tests/Release lint/R8 APK+AAB gate passed; real-device GPU/visual acceptance remains the device checkpoint**

## Goal

S5 adds restrained, category-specific natural micro-detail without changing weather truth, scenery selection, S4 variations, or the frozen cloud renderer.

The details are intentionally subtle. They support depth and material realism instead of turning the wallpaper into a busy illustrated scene.

## Scene behavior

### Open Sky

Open Sky remains intentionally clean. S5 does not add foreground objects that would compete with the sky, Sun, Moon, stars or weather.

### Natural Hills

- adds low-contrast terrain/ridge texture inside the established mid-distance land mass,
- texture is visibility-aware and softens under fog, haze and storm darkness,
- no new moving terrain or fake atmospheric event is introduced.

### Village

- adds a restrained irregular hedge/field-edge band,
- keeps existing generic houses, roofs and curved path,
- avoids fences, landmark replicas or high-contrast cartoon silhouettes.

### Farm / Crops

- adds sparse crop-head/high-frequency field texture on top of the existing perspective rows,
- crop micro-detail follows the already-authoritative current wind sway,
- fog/haze suppresses the detail naturally.

### River / Lake

- adds restrained bank reeds near the water edge,
- reeds reuse current wind-driven sway,
- water waves and Sun/Moon reflections remain driven by existing current conditions.

### Flowers / Greenery

- adds fine grass/leaf depth beneath the existing flower distribution,
- movement uses only current wind strength,
- flower and grass detail attenuates with visibility loss.

### Urban / Buildings

- adds restrained rooftop edge/material rhythm,
- does not fabricate real buildings or landmarks,
- existing night-window behavior remains scene-light/night controlled.

## Visibility and weather invariants

A shared `detailVis` factor suppresses micro-detail when visibility is poor or storm darkness is strong.

S5 does not create or suppress weather:

- rain/drizzle still alone control wetness,
- fog/haze still control distance visibility,
- storm state still controls storm darkness,
- Sun/Moon state still controls reflections and lighting,
- current wind remains the only motion driver for vegetation/crop/reed detail,
- forecast probability is never converted into current precipitation or storm visuals.

## Performance invariants

- no new bitmap or texture asset,
- no scenery network request,
- no SharedPreferences/disk access in the GL frame hot path,
- no new per-frame Java object allocation,
- no loops were added to the fragment shader,
- S5 is implemented as restrained analytic shader detail on the existing world pass.

## Cloud freeze boundary

S5 does not modify `HeroGlTextureCloudRenderer.java`.

Verified active cloud blob:

`dc3b5db66c92cdf4520b0210857426e4bca853d8`

## Automated verification

Authoritative source gate:

- source commit: `6eb342aeecd538d2d4c573f40abeda8414b9ca8e`
- GitHub Actions run: `31999015447`

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

1. Pull `main` and open the Wallpaper page.
2. Check Natural Hills in daylight and confirm ridge detail is subtle, not noisy.
3. Check Village and confirm the hedge/field-edge detail supports the houses rather than dominating them.
4. Check Farm and confirm crop-head detail follows the existing field perspective and does not sparkle like particles.
5. Check River/Lake and confirm reeds stay close to the bank and water remains visually dominant.
6. Check Flowers/Greenery and confirm fine grass depth does not create flicker or moire.
7. Check Urban and confirm roof-edge detail remains restrained.
8. Check Open Sky and confirm no new foreground clutter was introduced.
9. Cycle S4 variations 1-4 and confirm S5 details remain stable through the 1.8 second morph.
10. Check fog/haze/storm conditions when available and confirm micro-detail attenuates naturally.
11. Confirm Home Hero and Android Live Wallpaper remain visually aligned.
12. Confirm cloud shape/motion is unchanged.
13. Check `LiveWeatherGL` logs for world-shader compile/link errors.

## Next scenery step

After real-device S5 acceptance, the next step can focus on **S6 — Time-of-day Scenery Lighting & Material Response**: subtle dawn/golden-hour/night adaptation for land, village, crops, water and urban materials while preserving authoritative Sun/Moon/weather truth.
