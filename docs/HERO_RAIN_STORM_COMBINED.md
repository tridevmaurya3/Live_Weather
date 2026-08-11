# Hero Rain / Storm / Cloud Combined Upgrade

Status: COMPLETE — HRS-1B + HRS-2 + HRS-3 code integration

## Permanent Hero contract

The live weather scene must feel continuously alive while remaining driven by real weather data. The main atmosphere must not fall back to icons, finite canned animations, moving rectangular texture sheets, or a single set of short rain lines.

## HRS-1B — Continuous rain

- `HeroRainRenderer` owns rain/drizzle visuals in app previews and the system Live Wallpaper.
- Legacy `NatureSceneRenderer` rain is disabled at integration points.
- Far, mid and near rain layers recycle deterministically for as long as rain is active.
- Animation uses renderer-local elapsed time instead of epoch seconds stored in `float`; this preserves sub-second motion precision during long runtimes.
- A short rain-signal hold prevents one transient current/minutely model dip from abruptly stopping an otherwise wet scene.
- Wind direction and speed influence rain slant and horizontal travel.
- Heavy rain adds a full-screen atmospheric veil and dense far-rain curtain.
- `WetGlassOverlay` adds sliding foreground droplets, specular rims and water trails.

## HRS-2 — Storm and lightning

- `HeroStormRenderer` owns lightning visuals at integration points; legacy lightning is disabled.
- Lightning is enabled only for thunderstorm WMO conditions; strong rain alone does not fabricate electrical activity.
- `StormFlashController` produces irregular multi-pulse event windows rather than a fixed metronome cadence.
- `LightningBoltGenerator` creates deterministic branched bolts with glow and bright electrical cores.
- A strike illuminates the whole scene, adds a radial cloud/sky glow and leaves a short afterglow.
- Wet-glass highlights receive the same flash strength so foreground water reacts to lightning.

## HRS-3 — Natural cloud pipeline

- `HeroCloudRenderer` owns visible clouds in app previews and the system Live Wallpaper; legacy bitmap clouds are disabled at integration points.
- Clouds are closed irregular Bezier masses, not scaled rectangular bitmaps.
- Therefore no cloud bitmap boundary can appear as a moving square/rectangle.
- Current cloud cover, nearest 15-minute cloud cover and resolved WMO condition are combined.
- Partly cloudy, rain, overcast and thunderstorm conditions enforce restrained cloud-presence floors so a wet/partly-cloudy state cannot render as an empty blue sky because of one low model sample.
- Mixed weather uses scattered cumulus/thin layers; rain uses lower darker stratus/rain-cumulus; thunderstorms use a dense storm ceiling.
- Cloud motion uses local elapsed animation time and bounded vertical drift, preventing long-runtime float precision loss or clouds drifting permanently off-screen.

## Shared integration

`LiveSkyView` and `LiveWeatherWallpaperService` use the same visible order:

1. Base sky, Sun, Moon, stars, snow and fog from `NatureSceneRenderer`.
2. Hero path-based clouds.
3. Storm atmosphere / cloud illumination.
4. AQI haze.
5. Continuous Hero rain + wet glass.
6. Foreground lightning branches / high-energy flash.

Cloud, rain and lightning switches still work, but each switch now controls the Hero renderer rather than enabling the legacy duplicate visual.

## Lifecycle and battery

- The system wallpaper frame loop continues while Android reports the wallpaper surface visible.
- Normal target interval remains 33 ms, adaptive low-battery interval 50 ms and power-saver interval 66 ms.
- Reduced FPS must not end the rain animation; particles are clock-driven and recycled.
- Hidden wallpaper surfaces pause rendering as required for battery efficiency.
- No weather network request is made from any animation frame.

## Reality boundary

The visual is a high-fidelity procedural simulation driven by live/model weather and astronomy. It is not camera footage, a satellite image of the exact cloud shape overhead, or a pre-recorded rain video. The app should preserve this distinction while maximizing natural visual feel.
