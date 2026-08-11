# Live Weather — Visual Realism Upgrade

Status: COMPLETE — HIGH-FIDELITY PROCEDURAL REALISM

This upgrade replaces the earlier placeholder-like atmospheric drawing with a shared high-fidelity procedural scene used by the app and Android Live Wallpaper.

## Reality rule

The scene is generated from real app state:

- active latitude / longitude
- current clock
- Astronomy Engine Sun/Moon position and lunar phase
- resolved live weather condition
- cloud cover
- precipitation / rain / showers / snow
- fog / visibility
- wind speed and direction
- storm intensity
- AQI haze overlay where available

It is not a camera feed and is not pre-recorded weather video. The visual scene is a procedural simulation driven by the live data above.

## RU-1 — Cloud Realism Engine Rewrite

Status: COMPLETE

- Removed the old overlapping circle/oval cloud construction from the primary renderer.
- Added cached procedural fractal-density cloud textures.
- Added feathered irregular cloud edges.
- Added internal density variation.
- Added upper-light / lower-shadow cloud volume passes.
- Added separate Cirrus, Cumulus, Stratus and Storm cloud profiles.
- Added multiple texture variants to reduce obvious repetition.
- Added far / mid / near depth layers.
- Added depth-dependent scale, opacity and parallax.
- Wind speed and direction drive cloud travel.
- Heavy rain/storm states force denser and darker cloud profiles.
- Clouds render after Sun/Moon, creating natural celestial obstruction.

## RU-2 — Rain / Snow / Fog / Storm Realism

Status: COMPLETE

- Drizzle and rain use different density, length, speed and opacity profiles.
- Rain renders in multiple depth layers.
- Wind direction changes rain slant and horizontal travel.
- Heavy rain adds lower-scene splash detail and precipitation veil.
- Snow has multiple depth sizes, fall speeds and flutter motion.
- Wind affects snow drift.
- Fog uses cached noise-textured mist bands instead of flat oval overlays.
- Fog bands move at different depths and speeds.
- Thunderstorms use irregular flash timing rather than constant blinking.
- Lightning has a bright core, soft glow and secondary branches.
- Storm intensity darkens the whole atmosphere coherently.

## RU-3 — Sun / Moon / Stars Realism

Status: COMPLETE

- Removed cartoon-like rotating Sun spokes.
- Sun uses atmospheric bloom, corona and a luminous physical-looking disc.
- Sunrise/sunset horizon scattering is rendered separately from the Sun disc.
- Moon uses a cached procedural crater/albedo texture.
- SkyRealityState now exposes the real astronomical Moon phase angle.
- Moon illumination is rendered as a sphere-lighting calculation: 0° new, 90° first quarter, 180° full, 270° third quarter.
- Waxing/waning illumination direction comes from the phase angle instead of a phase-name icon approximation.
- Moon halo strength follows visible illuminated fraction.
- Star field uses several brightness/color classes and subtle twinkle.
- Star field drifts slowly on a sidereal-day timescale for a natural night-sky feeling.
- Existing cloud/fog/rain/Moon-glare visibility logic continues to suppress stars when the real sky should be obscured.

## RU-4 — Cinematic Atmosphere Composer

Status: COMPLETE

- Daylight, golden hour, civil twilight, nautical twilight, astronomical twilight and night have distinct sky gradients.
- Sun altitude adds localized horizon scattering around sunrise/sunset.
- Moonlight adds restrained night luminance.
- Cloud/rain/fog/storm values dim the scene continuously instead of selecting unrelated static backgrounds.
- Weather state changes cross-fade over several seconds to avoid abrupt clear/rain visual jumps.
- Ground/horizon atmosphere changes with cloud, fog and precipitation intensity.
- App background, Forecast Live Sky, Wallpaper preview and Android WallpaperService continue to consume the same NatureSceneRenderer.
- Existing AQI haze post-processing remains compatible with the upgraded renderer.

## Performance contract

- Main animation remains Canvas-based and compatible with the project's minimum Android version.
- Cloud/fog/moon pixel textures are generated once per variant/state and cached.
- Animation frames scale/tint/reuse cached textures rather than rebuilding cloud pixels every frame.
- Weather network refresh remains outside the rendering loop.
- Astronomy/environment reality is refreshed periodically, not every animation frame.
- WallpaperService still stops its frame loop while not visible and retains adaptive FPS behavior.

## Accuracy boundaries

Visual realism does not mean a direct photograph of the sky. Exact local cloud shapes, individual raindrop paths, exact visible stars, buildings, terrain and light pollution cannot be reconstructed from the current forecast/model feeds alone.

The engine therefore follows this rule:

**real data controls state, intensity, motion, visibility, astronomical position and lighting; procedural graphics provide the visual representation.**
