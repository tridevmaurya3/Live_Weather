# Hero OpenGL Weather Engine Migration

Status: STAGE 1 ACTIVE — SYSTEM LIVE WALLPAPER GPU PATH

## Purpose

The Hero weather experience is migrating from Canvas/path sprites to an original OpenGL ES weather renderer. The goal is a continuous, high-fidelity atmosphere without rectangular bitmap artifacts or cartoon cloud rows.

The attached/reference application was studied only for rendering architecture ideas. Its copyrighted graphics are not copied. Live Weather uses its own shader-generated sky, clouds, rain, wet-glass, lightning, Moon and stars.

## Stage 1 Architecture

- `HeroGlSceneRenderer` — OpenGL ES 2.0 full-screen fragment-shader renderer.
- `GlSceneSnapshot` — immutable normalized GPU state.
- `GlRealityAdapter` — converts shared weather + AQI + astronomy reality into GPU uniforms.
- `GlWallpaperRenderThread` — dedicated EGL14 render thread attached to `WallpaperService.Engine` surface.
- `LiveWeatherWallpaperService` — lifecycle/cache coordinator only; no Canvas animation loop.

Network/weather refresh remains outside the render loop. The GL thread animates continuously while the wallpaper is visible and periodically refreshes the local astronomy/reality snapshot.

## Celestial Reality Contract

### Moon position

Moon altitude and azimuth come from the existing Astronomy Engine / `SkyRealityEngine`. The GL renderer does not invent a Moon position. Below-horizon Moon visibility is zero.

### Moon phase

The GPU receives the real astronomical lunar phase angle. The fragment shader treats the Moon as a sphere and computes the illuminated limb/terminator from that phase angle. This supports new moon, crescent, quarter, gibbous and full moon continuously instead of switching icon assets.

The shader must never replace a thin crescent with a full grey or black disc.

### Daytime Moon visibility

`DynamicRealityComposer` remains the authority for Moon visibility. It combines atmospheric transparency and daylight contrast without multiplying lunar illumination twice. Near-new-moon daylight can be effectively invisible; a meaningful crescent can remain faintly readable when the Moon is above the horizon.

### Stars

Star visibility remains reality-driven:

- astronomical darkness / twilight state,
- cloud cover,
- fog,
- rain/snow/storm opacity,
- atmospheric visibility,
- AQI aerosol haze,
- Moon glare as already represented by the astronomy sky state.

Stars must not appear as decorative dots in daylight, heavy overcast, dense fog or strong precipitation.

## GPU Visual Layers in Stage 1

- time-of-day sky gradient,
- horizon haze,
- procedural star field with subtle twinkle,
- luminous Sun,
- phase-correct Moon + halo,
- full-screen fractal cloud field with wind motion,
- continuous multi-depth rain,
- wet-glass droplets,
- storm-darkening,
- irregular full-screen lightning flash,
- procedural lightning channel/branches,
- rain/fog ground mist.

## Performance Contract

- EGL/OpenGL rendering runs on a dedicated `HandlerThread`.
- Weather/network data is never fetched per frame.
- Reality/astronomy snapshot refresh is approximately every 30 seconds.
- Wallpaper rendering stops when Android reports the wallpaper hidden.
- Normal animation target is about 30 FPS.
- Battery adaptive mode reduces target FPS under low battery / Power Saver.
- OpenGL context/surface resources are recreated when the wallpaper surface is recreated.

## Visual Settings Contract

Rain, Clouds, Lightning, Snow, Fog and Stars preferences continue to control the corresponding GPU visual intensities. Sun/Moon/time/astronomy reality sync remains always active.

## Migration Safety

Stage 1 switches the actual Android system Live Wallpaper to the GPU path. In-app `LiveSkyView` remains on the current Canvas Hero renderer until Stage 1 is validated on a real device. After validation, Stage 2 will migrate the app/Forecast/Wallpaper preview to the same GL scene engine so all surfaces are visually identical.
