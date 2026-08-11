# Hero Rain & Storm Upgrade

## Goal

The weather scene must feel live rather than icon-based or particle-placeholder based. Rain, storm, lightning, clouds and wet atmosphere are treated as Hero visuals for the app and Android Live Wallpaper.

## HRS-1A — Real Rain + Wet Screen Foundation
Status: COMPLETE — DEVICE VALIDATION PENDING

### Rendering contract
- NatureSceneRenderer legacy rain streaks are disabled in app LiveSkyView and Android WallpaperService.
- HeroRainRenderer owns rain/drizzle foreground rendering.
- Three depth layers are used: far atmospheric rain, mid streaks and near motion-blurred drops.
- Wind direction and speed affect streak slant and horizontal travel.
- Heavy rain adds a full-screen atmospheric curtain without moving bitmap rectangles.
- Foreground wet-glass droplets slide independently of distant rain and include restrained rim/highlight/trail cues.
- Drizzle is thinner, slower and less visually dominant than rain.
- The same HeroRainRenderer is shared by in-app live sky surfaces and the system Live Wallpaper.
- No weather network request, bitmap synthesis or blocking work is performed from the rain frame loop.

## HRS-2 — Storm + Lightning Realism
Status: NEXT

Planned: storm-cloud illumination, full-scene flash, branched lightning energy, multi-pulse afterglow and severe-storm lighting response.

## HRS-3 — Cloud / Rain-Cloud Final Realism
Status: PLANNED

Planned: final cloud artifact checks, rain-cloud structure, mixed-sky presentation and storm ceiling depth.

## HRS-4 — Cinematic Atmosphere Polish
Status: PLANNED

Planned: wet horizon, mist response, Sun/Moon obstruction, post-rain atmosphere and final hero-quality transitions.
