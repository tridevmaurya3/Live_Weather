# Hero Rain & Storm Upgrade

## Goal

The weather scene must feel live rather than icon-based or particle-placeholder based. Rain, storm, lightning, clouds and wet atmosphere are treated as Hero visuals for the app and Android Live Wallpaper.

## HRS-1A — Real Rain + Wet Screen Foundation
Status: COMPLETE

- NatureSceneRenderer legacy rain streaks are disabled in app LiveSkyView and Android WallpaperService.
- HeroRainRenderer owns rain/drizzle foreground rendering.
- The same Hero rain layer is shared by in-app live sky surfaces and the system Live Wallpaper.

## HRS-1B — Continuous Cinematic Rain
Status: COMPLETE — DEVICE VISUAL VALIDATION REQUIRED

- Rain is clock-driven and deterministically recycled while the scene remains visible.
- The animation has no finite particle lifetime that can run out.
- Far, mid and near rain use different density, length, speed, alpha and motion character.
- Per-drop variation prevents a wall of identical bright vertical lines.
- Wind direction and speed influence slant and horizontal travel.
- Slow gust modulation keeps the rain field alive without random per-frame jitter.
- Heavy rain adds a full-screen atmospheric curtain and lower spray.
- WetGlassOverlay adds foreground beads, slow sliding droplets, trails and heavy-rain water channels.

## HRS-2 — Storm + Lightning Realism
Status: COMPLETE — DEVICE VISUAL VALIDATION REQUIRED

- Electrical effects are enabled only for thunderstorm WMO weather codes.
- Rain alone never fabricates lightning.
- StormFlashController creates irregular multi-pulse lightning events indefinitely while a thunderstorm state remains active.
- HeroStormRenderer darkens the scene, illuminates the cloud ceiling, creates a broad local strike glow and flashes the whole screen.
- Close strikes render deterministic branched lightning through LightningBoltGenerator.
- Distant strikes can illuminate clouds without forcing a dominant foreground bolt.
- Foreground water receives current lightning strength so wet glass catches the electrical flash.

## HRS-3 — Natural Cloud / Rain-Cloud Realism
Status: COMPLETE — DEVICE VISUAL VALIDATION REQUIRED

- Legacy cloud sprites are disabled in the Hero pipeline.
- HeroCloudRenderer uses Path geometry, not scaled rectangular cloud bitmaps.
- Cloud contours use broad smooth anchor curves instead of repeated semicircle/scallop lobes.
- Rain and storm states use fewer, larger cloud banks instead of rows of small repeated shapes.
- Current cloud cover, nearest 15-minute cloud cover and WMO weather condition jointly determine visible cloud amount.
- Wind continuously moves cloud banks.
- Storm/rain cloud banks use darker lower tones and restrained upper light.

## Unified renderer order

1. NatureSceneRenderer: sky, Sun, Moon, stars, snow and fog.
2. HeroCloudRenderer: weather-driven clouds in front of celestial objects.
3. HeroStormRenderer atmosphere: storm darkening and cloud illumination.
4. AirHazeOverlayRenderer.
5. HeroRainRenderer: continuous rain and wet glass, receiving lightning strength.
6. HeroStormRenderer foreground: visible lightning branches and final electrical pulse.

## HRS-4 — Cinematic Atmosphere Polish
Status: NEXT

Planned: final device-driven tuning of cloud scale/opacity, wet horizon, post-rain atmosphere, Sun/Moon obstruction, storm pacing and transition polish.

## Accuracy rule

The visual engine is a high-fidelity procedural representation driven by weather and astronomy data. It is not a camera feed and it must not invent clouds, lightning or precipitation that contradict the resolved weather state.
