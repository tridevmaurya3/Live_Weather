# Scenery S10 — Auto Scene Intelligence + Favorites / Quick Presets

Status: **SOURCE IMPLEMENTATION COMPLETE — latest combined main gate passed; real-device UX acceptance remains the device checkpoint**

## Goal

S10 upgrades Auto Scene from day-part-only presentation selection to current-condition-aware presentation selection and adds fast user shortcuts without changing weather truth.

## Auto Scene intelligence

Auto Scene now considers only authoritative current resolved scene values already present in `GlSceneSnapshot`:

- current cloud cover,
- current rain intensity,
- current drizzle intensity,
- current snow intensity,
- current storm intensity,
- current fog intensity,
- current haze intensity.

Forecast probability is not used to fabricate a current condition.

Priority presentation pools are intentionally restrained:

1. current storm truth,
2. current snow truth,
3. current fog/haze truth,
4. current rain/drizzle truth,
5. strongly cloudy current truth,
6. otherwise the existing dawn/day/evening/night policy.

Every result is a concrete scenery mode. AUTO itself is never treated as a weather state.

## Hot-path boundary

The shared `HeroGlPipeline` updates Auto Scene context only when a new authoritative snapshot/options state arrives.

`drawFrame()` does not:

- read SharedPreferences,
- read weather cache,
- read the network,
- calculate Auto Scene policy,
- allocate Auto Scene objects.

`AutoSceneryContextState` keeps the latest current-condition primitives process-locally so variation/options changes can re-resolve Auto without a storage/network read.

## App + Live Wallpaper parity

The same shared pipeline performs current-condition Auto resolution for both:

- in-app Hero/preview,
- Android Live Wallpaper.

Scenery identity remains presentation-only. Rain/cloud/storm/fog/snow/Sun/Moon values are never modified by the policy.

## Quick presets

The Wallpaper selector now exposes one-tap manual presets:

- Sky — Open Sky, variation 1,
- Green — Flowers / Greenery, variation 2,
- Water — River / Lake, variation 3,
- City — Urban / Buildings, variation 1.

A quick preset changes only scenery + variation and deliberately becomes a manual scene selection.

## Favorites

Users can save up to three scene + variation combinations.

- `☆ Save current` stores the current concrete scene and selected variation.
- If Auto Scene is selected, Save Current stores Auto's currently resolved concrete scene, not the AUTO token.
- `★ Saved` indicates that the current combination is already in favorites.
- Saving a fourth favorite drops the oldest saved favorite so the three most recent remain available.
- Tapping a favorite applies it as a manual scene + variation.
- Long-pressing a favorite removes it.
- Favorite storage occurs only from Wallpaper selector UI and never from the GL frame loop.

## Selector UX

S10 preserves:

- Auto Scene + seven manual scene chips,
- direct variation 1 / 2 / 3 / 4 chips,
- 48dp touch targets,
- selected-state styling,
- accessibility descriptions/announcements,
- existing smooth scenery/variation transitions.

It adds:

- current-condition-aware Auto helper text,
- Quick Presets section,
- Favorites section,
- one-tap favorite restore,
- long-press favorite removal.

## Cloud freeze boundary

S10 does not modify `HeroGlTextureCloudRenderer.java`.

Verified latest-main cloud blob:

`dc3b5db66c92cdf4520b0210857426e4bca853d8`

## Source checkpoints

S10 source checkpoint before unrelated concurrent Reality commits:

`e6c453d098946e6a9d9437b4f04948ad3eb9797f`

After S10, unrelated Reality commits modified star/fog/storm rendering and one pipeline line while preserving S10 files. Latest combined validated main head:

`49395d5d3613c3ffa2d80a0d85faf68a54133d31`

GitHub Actions authoritative combined run:

`32003788817`

Passed:

- Gradle wrapper verification,
- Debug build,
- unit tests including current-condition Auto Scene policy coverage,
- Release lint,
- minified/R8 Release APK,
- Release AAB,
- release output verification,
- release verification artifact upload.

## Real-device acceptance checklist

1. Pull latest `main` and open Wallpaper.
2. Select Auto Scene and confirm `Auto now · <scene>` is shown.
3. Confirm changing rain/cloud/etc. switches does not turn Auto into manual mode.
4. Confirm current observed rain/fog/snow/storm conditions can influence only Auto's scenery choice when those conditions actually exist.
5. Test Quick Presets: Sky, Green, Water, City.
6. Save three different scene + variation favorites.
7. Confirm tapping a favorite restores both scene and variation.
8. Save a fourth favorite and confirm only three recent favorites remain.
9. Long-press a favorite and confirm it is removed.
10. With Auto selected, Save Current should save the concrete `Auto now` scene.
11. Confirm App Hero, preview and Live Wallpaper remain aligned.
12. Confirm clouds retain the frozen accepted renderer shape/motion.

## Next scenery step

After device acceptance, the next step can focus on **S11 — Scene Library Presentation + Preview Cards**, making scene discovery more visual and easier to compare without changing the weather/render truth pipeline.
