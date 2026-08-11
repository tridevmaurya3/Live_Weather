# Hero OpenGL Weather Engine Migration

Status: ODM-1 THROUGH ODM-5 CODE COMPLETE — FINAL REAL-DEVICE VISUAL ACCEPTANCE PENDING

## Purpose

The Hero weather experience has migrated from Canvas/path-based weather visuals to an original OpenGL ES 2.0 rendering architecture. The goal is a continuous, high-fidelity atmosphere without rectangular bitmap artifacts, cartoon cloud rows or separate visual engines for the app and Android Live Wallpaper.

The external/reference application was studied only for rendering architecture ideas. Its copyrighted graphics are not copied. Live Weather uses original procedural/GPU sky, clouds, rain, wet-glass, lightning, Moon and stars.

## Current shared architecture

- `HeroGlPipeline` — single rendering ownership/order shared by app and system wallpaper.
- `HeroGlCloudSceneRenderer` — sky, Sun, Moon, stars and multi-depth clouds.
- `HeroGlAtmosphereOverlayRenderer` — cinematic distance atmosphere and horizon scattering.
- `HeroGlStormOverlayRenderer` — storm atmosphere and electrical effects.
- `HeroGlRainOverlayRenderer` — drizzle/rain depth and wet-screen effects.
- `GlSceneSnapshot` — immutable normalized GPU state.
- `GlRealityAdapter` — converts shared weather + AQI + astronomy reality into GPU values.
- `CloudPresenceResolver` — resolves cloud mode, amount, density, far/mid/near presence, storm ceiling and brightness.
- `SkyGradientProfile` — time-of-day + weather-aware sky palette.
- `LiveSkyView` — in-app `TextureView` + EGL14 surface using `HeroGlPipeline`.
- `GlWallpaperRenderThread` — Android system wallpaper EGL14 thread using the same `HeroGlPipeline`.
- `LiveWeatherWallpaperService` — wallpaper lifecycle/cache coordinator.

Network/weather refresh remains outside all frame loops.

## Original Design Match stages

### ODM-1 — Sky + Cloud Foundation

- Weather-aware clear/partly-cloudy/overcast/rain/storm/twilight/night palettes.
- Structured cloud presence from current + near-term weather reality.
- Independent far/mid/near cloud fields and storm ceiling.
- Wind-driven depth/parallax, turbulence, feathered edges and lighting.
- Strict gates against decorative clouds, rectangles and one-sheet grey wash.

### ODM-2 — Rain + Wet Screen

- Dedicated transparent GPU pass.
- Drizzle separate from normal/heavy rain.
- Fine/far/mid/near streak depth.
- Wind response and heavy-rain curtain.
- Fixed/sliding wet-glass droplets, trails, lower wet film and restrained splashes.

### ODM-3 — Storm + Lightning

- Dedicated storm/electrical pass.
- Short irregular main channel and smaller branches.
- Localized cloud illumination.
- Restrained multi-pulse exposure flash.
- Rain exposure response synchronized with storm timing.
- Legacy full-height/cable-like lightning removed from the active pipeline.

### ODM-4 — Moon + Stars + Night

- Astronomy remains authoritative for Sun/Moon position and Moon phase.
- Continuous phase-correct lunar sphere shading.
- Lunar limb, maria/crater variation and subtle earthshine.
- Daylight/twilight/weather visibility remains reality-driven.
- Multiple star brightness classes, subtle colour-temperature differences, twinkle, horizon extinction and Moon-glare suppression.
- Night retains vertical depth and illuminated-Moon sky fill without fake decorative glow.

### ODM-5 — Final Atmosphere + App/Wallpaper Match

- Added restrained cinematic atmosphere pass for distance depth, lower-horizon haze/fog, low-Sun scattering, lunar fill and storm/rain atmosphere.
- Migrated app `LiveSkyView` from Canvas renderers to `TextureView` + EGL14.
- App global background, Forecast live-sky preview, Wallpaper settings preview and Android system Live Wallpaper now use the same `HeroGlPipeline`.
- App live weather/AQI/location/options state is shared between visible preview surfaces.
- Hidden app previews stop scheduling frames.
- GPU preview surfaces respect rounded/card clipping.
- EGL surface recreation reuses programs while the context remains alive and releases programs at context shutdown.

## Celestial reality contract

### Moon position

Moon altitude and azimuth come from Astronomy Engine / `SkyRealityEngine`. Below-horizon visibility is zero.

### Moon phase

The GPU receives real astronomical lunar phase angle and illumination. The shader owns phase geometry; the composer must not multiply visibility by lunar illumination a second time.

### Daytime Moon

`DynamicRealityComposer` remains authority for atmospheric/daylight Moon visibility. A meaningful crescent can remain faintly visible when geometry and atmospheric conditions allow it; near-new Moon in daylight may be effectively invisible.

### Stars

Stars are controlled by astronomical darkness plus cloud, fog, precipitation, atmospheric visibility and AQI haze. They are not decorative daylight dots.

## Rendering order

Every active Hero GPU surface uses the same order:

1. Sky + celestial + cloud foundation.
2. Cinematic atmosphere.
3. Storm/electrical layer.
4. Rain/wet-screen foreground.
5. Surface buffer swap.

## Performance contract

- Rendering runs off the UI thread.
- No network call or texture generation occurs per frame.
- Reality/astronomy snapshots refresh periodically rather than every frame.
- Hidden app previews stop their animation loop.
- System wallpaper stops while Android reports it hidden.
- Inactive rain and storm passes early-out.
- System wallpaper retains battery-adaptive FPS behavior.
- EGL shader programs are not recreated for every surface recreation when the same context remains alive.

## Visual settings contract

Rain, Clouds, Lightning, Snow, Fog and Stars settings continue to control the corresponding visual layers. Sun/Moon/time/astronomy reality remains synchronized independently of decorative settings.

## Final acceptance

ODM-1 through ODM-5 are code-complete. Exact visual acceptance against the original generated concept is intentionally not claimed from source code alone. Final acceptance requires a real-device combined review of clear/cloudy/rain/storm/night scenes, UI readability and performance.
