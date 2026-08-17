# Scenery S9 — UX Polish + Auto Scene Foundation

Status: **SOURCE IMPLEMENTATION COMPLETE — automated Debug/tests/Release lint/R8 APK+AAB gate passed; real-device UX acceptance remains the device checkpoint**

## Goal

S9 makes scenery selection clearer and introduces an optional Auto Scene mode without changing weather truth, astronomy truth or the frozen cloud renderer.

## Auto Scene behavior

Auto Scene is a presentation-only scenery choice.

- It never creates, suppresses or reinterprets rain, clouds, storm, fog, snow, wind or astronomy.
- It resolves outside the OpenGL frame hot path.
- It uses a stable local day-part policy rather than random per-frame/per-minute changes.
- Dawn, daytime, evening and night each use a small appropriate scenery family.
- Day-of-year + selected variation provide repeatable variety inside the active day-part family.
- The resolved result is always one concrete manual scenery mode; AUTO itself is never sent as a fake weather state.

## Requested vs resolved scene identity

`SceneryRuntimeState` now separates:

- requested mode — what the user selected (`AUTO` or a manual scene),
- resolved mode — the concrete scene currently rendered.

This prevents legacy visual-option constructors from accidentally replacing persisted Auto Scene with its current concrete resolved scene when the user toggles rain/cloud/lightning/snow/fog/stars/battery options.

## Wallpaper preference integration

`WallpaperPreferences` now:

- initializes the existing scenery variation state,
- persists the requested scenery identity,
- resolves Auto Scene outside the GL frame loop,
- refreshes Auto resolution when needed,
- preserves AUTO through the existing seven-argument compatibility `Options` constructor.

The Android Live Wallpaper already reloads preferences on its existing visible cache cycle, so Auto Scene can refresh without introducing a new timer or network request.

## Selector UX

The Wallpaper scenery selector now exposes eight choices:

1. Auto Scene
2. Open Sky
3. Natural Hills
4. Village
5. Farm / Crops
6. River / Lake
7. Flowers / Greenery
8. Urban / Buildings

UX changes:

- Auto Scene is the first compact chip.
- Manual selection shows `Selected scene · …`.
- Auto selection shows `Auto now · …` using the concrete resolved scene.
- A short helper line explains Auto/manual behavior.
- Variation selection is now direct using 1 / 2 / 3 / 4 chips instead of repeatedly pressing a cycle-only action.
- All chips retain 48dp touch targets and selected-state styling.
- Accessibility descriptions and announcements remain available.

## Auto day-part pools

Current S9 foundation policy:

- Dawn (05–08): Natural Hills / River-Lake / Village / Open Sky
- Day (09–15): Farm-Crops / Flowers-Greenery / Open Sky / Village
- Evening (16–19): River-Lake / Natural Hills / Village / Farm-Crops
- Night (20–04): Urban-Buildings / Natural Hills / River-Lake / Open Sky

The selected variation and day-of-year choose a repeatable member of the relevant pool.

## Performance invariants

- no new network request,
- no new GL-frame SharedPreferences read,
- no GL-frame wall-clock/day-part calculation,
- no new bitmap/texture asset,
- no new per-frame Java allocation,
- existing 1.8-second scenery transition remains responsible for visual scene changes.

## Weather-truth invariants

Auto Scene changes only scenery identity.

- live rain/drizzle truth remains unchanged,
- live cloud truth remains unchanged,
- live storm/lightning truth remains unchanged,
- live fog/haze truth remains unchanged,
- live snow truth remains unchanged,
- live wind truth remains unchanged,
- Sun/Moon/time-of-day material response remains driven by existing astronomy state.

## Cloud freeze boundary

S9 does not modify `HeroGlTextureCloudRenderer.java`.

Verified active cloud blob:

`dc3b5db66c92cdf4520b0210857426e4bca853d8`

## Automated verification

Authoritative source checkpoint:

- final source commit: `46a6d7855b1ce74c6dcde9b4a3ef4e57e619e4c5`
- GitHub Actions run: `32002285521`

Passed:

- Gradle wrapper verification,
- Debug build,
- unit tests,
- Auto Scene policy unit coverage,
- Release lint,
- minified/R8 Release APK,
- Release AAB,
- release output verification,
- release verification artifact upload.

## Real-device acceptance checklist

1. Pull `main` and open the Wallpaper page.
2. Confirm `Auto Scene` appears before the seven manual scenes.
3. Select Auto Scene and confirm the summary reads `Auto now · <scene>`.
4. Toggle Rain/Cloud/Lightning/Snow/Fog/Stars/Battery options and confirm Auto Scene remains selected.
5. Select a manual scene and confirm the helper explains it remains fixed.
6. Tap variation chips 1, 2, 3 and 4 directly and confirm selected styling follows the tapped value.
7. With Auto Scene selected, change variation and confirm `Auto now` can resolve a different suitable scene while weather effects remain unchanged.
8. Apply the Live Wallpaper and confirm the same scene choice is used.
9. Confirm scenery changes still use the existing smooth transition rather than a blank/hard cut.
10. Confirm cloud shape/motion remains unchanged.

## Next scenery step

After S9 device acceptance, the next step can be **S10 — Auto Scene Intelligence + Scene Favorites/Quick Presets**, where Auto policy can optionally use existing current-condition context only to choose presentation scenery, while still never changing or inventing weather truth.
