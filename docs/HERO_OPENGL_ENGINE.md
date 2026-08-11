# Hero OpenGL Weather Engine Migration

Status: STAGE 1 ACTIVE — ODM-1 SKY + CLOUD FOUNDATION COMPLETE

## Purpose

The Hero weather experience is migrating from Canvas/path sprites to an original OpenGL ES weather renderer. The goal is a continuous, high-fidelity atmosphere without rectangular bitmap artifacts or cartoon cloud rows.

The attached/reference application was studied only for rendering architecture ideas. Its copyrighted graphics are not copied. Live Weather uses its own shader-generated sky, clouds, rain, wet-glass, lightning, Moon and stars.

## Stage 1 Architecture

- `HeroGlCloudSceneRenderer` — active OpenGL ES 2.0 system Live Wallpaper renderer after ODM-1C/1D.
- `HeroGlSceneRenderer` — retained earlier GPU renderer/fallback reference.
- `GlSceneSnapshot` — immutable normalized GPU state.
- `GlRealityAdapter` — converts shared weather + AQI + astronomy reality into GPU uniforms.
- `CloudPresenceResolver` — resolves visual cloud mode, amount, density, far/mid/near presence, storm ceiling and brightness before rendering.
- `SkyGradientProfile` — resolves time-of-day + weather-aware top/mid/horizon atmospheric palettes.
- `GlWallpaperRenderThread` — dedicated EGL14 render thread attached to `WallpaperService.Engine` surface.
- `LiveWeatherWallpaperService` — lifecycle/cache coordinator only; no Canvas animation loop.

Network/weather refresh remains outside the render loop. The GL thread animates continuously while the wallpaper is visible and periodically refreshes the local astronomy/reality snapshot.

## ODM-1 — Original Design Match: Sky + Cloud Foundation

Status: COMPLETE IN CODE — REAL-DEVICE VISUAL ACCEPTANCE PENDING

### ODM-1A Sky Gradient Engine

- Replaced generic weather-darkening of a single sky palette with dedicated atmospheric profiles.
- Clear, partly cloudy, overcast, rain, storm, twilight and night retain different vertical colour depth.
- Haze/fog are weighted toward the horizon instead of washing the entire sky grey.

### ODM-1B Cloud Presence Resolver

- Cloud presence combines current cloud cover, nearest 15-minute cloud neighbourhood, resolved WMO condition, confirmed precipitation, storm, visibility and haze.
- Clear weather has no decorative cloud floor.
- Partly cloudy cannot become an empty sky.
- Confirmed precipitation receives a believable cloud source.
- Storm receives a dedicated ceiling value.
- GPU state carries cloud amount, density, far/mid/near layers, storm ceiling and brightness.

### ODM-1C GPU Cloud Field Renderer

- System Live Wallpaper switched to `HeroGlCloudSceneRenderer`.
- Clouds are split into independent far veil, mid clusters, near banks and storm ceiling.
- Layers use different scales, drift speeds, vertical envelopes and parallax response.
- No bitmap cloud rectangles are used by this active renderer.

### ODM-1D Motion, Lighting and Artifact Cleanup

- Parallax is centred around the neutral wallpaper offset so the centre page has no permanent directional bias.
- Far/mid/near layers use separate wind speeds and subtle cross-wind turbulence instead of scrolling as one sheet.
- Low-frequency domain warp breaks obvious repeating cloud-field shapes.
- Strict presence gates prevent faint full-screen cloud tint in genuinely clear conditions.
- Cloud edges receive restrained Sun-facing silver light; internal/storm mass remains darker.
- Storm ceiling has an independent dark ceiling blend.
- Sun/Moon obstruction uses the combined real cloud mask rather than total cloud percentage alone.
- Lunar limb masking uses a GLSL-safe smoothstep expression; Moon phase/position/visibility authority remains unchanged.

### ODM-1 Acceptance Contract

ODM-1 is accepted visually only when a real device shows all of the following:

- clear weather is genuinely clear,
- partly cloudy contains meaningful scattered masses,
- cloudy/overcast has broad layered coverage,
- far/mid/near clouds visibly differ in motion/depth,
- storm has a distinct upper dark ceiling,
- no rectangular/square moving cloud artifacts,
- no repeated scalloped/cartoon cloud rows,
- no single uniform grey sheet,
- cloud lighting has soft depth without looking painted or outlined.

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

- weather-aware time-of-day sky gradient,
- horizon haze,
- procedural star field with subtle twinkle,
- luminous Sun,
- phase-correct Moon + halo,
- multi-depth far/mid/near cloud fields,
- independent storm ceiling,
- continuous multi-depth rain,
- wet-glass droplets,
- irregular lightning flash/channel/branches,
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

Stage 1 switches the actual Android system Live Wallpaper to the GPU path. In-app `LiveSkyView` remains on the current Canvas Hero renderer until the system Live Wallpaper GPU path passes the real-device visual checkpoint. After that checkpoint, Stage 2 can migrate the app/Forecast/Wallpaper preview to the same GL scene engine so all surfaces are visually identical.
