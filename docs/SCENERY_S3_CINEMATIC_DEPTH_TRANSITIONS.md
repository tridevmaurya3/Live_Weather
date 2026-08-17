# Scenery S3 — Cinematic Depth + Smooth Scene Transitions

Status: **SOURCE IMPLEMENTATION COMPLETE — automated Debug/tests/Release lint/R8 APK+AAB gate passed; real-device GLSL/visual acceptance remains the device checkpoint**

## What S3 adds

S3 upgrades the shared scenery world pass without changing weather truth or the frozen cloud engine.

### Smooth scene transitions

- Scene changes no longer use one abrupt scenery id.
- The world renderer keeps a reusable `from` scene, `to` scene and eased transition mix.
- Transition duration is approximately 1.8 seconds.
- The shader blends every scenery channel between those two identities.
- Open-sky ground height, building footprint and window width also interpolate continuously instead of using hard threshold switches.
- No Java object is allocated per frame for the transition.

### Cinematic depth

The world pass now uses separate parallax coordinates for far, mid and near content:

- far terrain moves least,
- mid terrain / river structure moves moderately,
- near terrain, buildings, crops and flowers move most.

This increases perceived depth while keeping the existing wallpaper parallax source and without moving weather layers incorrectly.

### Terrain depth polish

- subtle far/mid ridge edge light,
- restrained near-ground contact shading,
- existing fog/haze continues to attenuate distant layers,
- storm and scene light remain sourced from current weather reality.

### Village / Urban

- deeper facade shading,
- Village keeps generic roofs and gains a restrained curved ground path,
- Urban keeps generic varied-height structures,
- no real landmark or location-specific building is fabricated.

### Farm / Crops

- primary perspective crop rows retained,
- finer secondary row texture added,
- crop-row motion responds subtly to current `windStrength` from `GlSceneSnapshot`,
- scenery does not invent wind.

### River / Lake

- far/mid/near parallax separates bank and water detail,
- large, medium and micro wave bands add depth,
- water glint is restrained and current Sun/Moon/fog aware,
- current precipitation still controls wet/reflection behavior.

### Flowers / Greenery

- meadow texture uses near-layer parallax,
- subtle vegetation/flower motion responds to current wind strength,
- flower accents remain restrained and fog attenuated.

## Weather-truth invariants

Scenery remains presentation-only:

- current rain/drizzle controls wetness,
- current fog/haze controls world attenuation,
- current storm controls storm darkness,
- current Sun/Moon visibility controls reflections,
- current wind strength alone drives crop/flower sway,
- scenery selection cannot create or suppress weather,
- forecast probability is never converted into current precipitation/storm visuals.

## Performance invariants

- no network request from the world renderer,
- no disk/SharedPreferences read in the frame hot path,
- no new per-frame Java object allocation,
- scenery transition state uses enum references plus primitive fields,
- adaptive frame/detail governor remains unchanged.

## Cloud freeze boundary

S3 does not modify `HeroGlTextureCloudRenderer.java`.

Verified active cloud blob:

`dc3b5db66c92cdf4520b0210857426e4bca853d8`

## Automated verification

Authoritative source gate:

- source commit: `e493438d2654cc802424e14269607d25471a19c8`
- GitHub Actions run: `31996723403`

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

GLSL programs are compiled by the phone GPU driver at runtime, so device visual acceptance remains required.

1. Open Wallpaper page and switch Natural Hills -> Village -> Farm -> River -> Flowers -> Urban -> Open Sky.
2. Confirm each change eases over roughly 1.8 seconds instead of cutting instantly.
3. Confirm there is no blank/black frame during transitions.
4. Confirm Natural Hills still looks like the established baseline.
5. Confirm parallax gives far/mid/near depth without excessive sliding.
6. On Farm, confirm crop rows look deeper and any motion remains subtle.
7. On Flowers, confirm greenery/flower motion remains subtle.
8. On River/Lake, confirm water has depth but is not mirror-like.
9. On Village/Urban, confirm structures remain supporting scenery rather than dominating the sky.
10. Confirm fog/rain/night still affect every scenery naturally.
11. Confirm clouds do not change shape/motion between scene selections.
12. Check `LiveWeatherGL` logs for any world shader compile/link error.

## Next scenery step

After S3 device acceptance, the next step can expand the scenery library with richer scene variations / sub-scenes while continuing to preserve the weather-truth and cloud-freeze boundaries.
