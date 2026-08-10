# Live Weather — Real Live Nature Engine

## Status

A. Animated Sky Engine — COMPLETE
B. Animated Weather Engine — COMPLETE
C. Dynamic Reality Composer — COMPLETE
D. Android Live Wallpaper Engine — COMPLETE

This document describes the shared procedural animation engine used by the app and Android home-screen Live Wallpaper.

## Product rule

The primary weather atmosphere must not use weather icons as a substitute for nature animation.

Small informational UI cards may still contain compact symbols, but the main animated atmosphere is drawn as a procedural scene driven by the shared weather + astronomy reality state.

## A. Animated Sky Engine

Implemented behavior:

- Real observer-relative Sun altitude and azimuth from Astronomy Engine.
- Sun disc, glow and animated rays.
- Sun position follows the active location and current clock.
- Real observer-relative Moon altitude and azimuth.
- Moon disc with phase-dependent shadow and visible surface variation.
- Moon phase / illumination comes from the shared astronomy state.
- Below-horizon Sun/Moon are not drawn overhead.
- Animated star field with different sizes, brightness and continuous twinkle.
- Star visibility is reduced by daylight, clouds, fog, precipitation and Moon glare.
- Daylight, golden hour, civil twilight, nautical twilight, astronomical twilight and night use different procedural sky gradients.
- Celestial state is locally recalculated on a controlled interval without a weather network request per frame.

## B. Animated Weather Engine

Implemented behavior:

- Multi-depth cloud layers.
- Cloud density follows live cloud cover.
- Cloud motion speed responds to wind speed.
- Cloud direction responds to wind direction.
- Rain uses animated streak particles.
- Drizzle uses lighter/slower particles.
- Rain angle responds to wind.
- Snow uses drifting variable-size particles.
- Fog uses moving translucent bands.
- Thunderstorm state can produce irregular screen flash and procedural lightning bolt animation.
- Weather intensity is derived from the precipitation-first resolved live condition instead of a decorative preset.

## C. Dynamic Reality Composer

`DynamicRealityComposer` converts one shared WeatherResponse + SkyRealityState into one renderer-facing SceneState.

SceneState includes:

- cloud cover
- rain intensity
- drizzle intensity
- snow intensity
- fog intensity
- storm intensity
- wind speed
- wind direction
- normalized wind strength
- visibility factor
- Sun visibility
- Moon visibility
- star visibility
- scene light

The composer deliberately combines astronomy and weather obstruction.

Examples:

- Clear astronomical night → stronger stars and visible Moon when above horizon.
- Rainy night → clouds/rain suppress stars and Moon visibility.
- Storm → darkened scene, dense cloud/rain and possible lightning.
- Fog → reduced celestial visibility and moving mist.
- Wind → faster cloud motion and more angled precipitation.
- Sunrise/sunset → changing celestial position plus gradual atmosphere lighting.

## D. Android Live Wallpaper Engine

`LiveWeatherWallpaperService` is registered as a real Android `WallpaperService`.

Implemented behavior:

- Uses the same NatureSceneRenderer as the in-app preview.
- Draws only while the wallpaper Engine is visible.
- Stops frame callbacks when hidden or when the surface is destroyed.
- Normal rendering targets approximately 30 FPS.
- Adaptive mode reduces FPS during system Power Saver or low battery.
- Home-screen horizontal offset is used as subtle scene parallax.
- Weather is read from the shared persistent WeatherCache.
- The service checks for a newer cached snapshot without networking every frame.
- WorkManager refreshes the latest active weather coordinates separately from rendering.
- Periodic refresh is constrained to a connected network.
- No background GPS permission is requested by the wallpaper engine.

The Android app's Apply button launches the system live-wallpaper preview/confirmation screen for `LiveWeatherWallpaperService`.

## Shared app animation

The app root also contains the same animated nature surface behind the UI.

Therefore these surfaces share one rendering architecture:

1. App animated background.
2. Forecast live-sky surface.
3. Wallpaper in-app preview.
4. Android home-screen Live WallpaperService.

All surfaces consume the same cached weather + astronomy truth rather than independent fake scenes.

## Persistent visual controls

The following optional visual layers are stored in shared preferences and affect app preview + system wallpaper:

- rain/drizzle
- cloud movement
- lightning
- snow
- fog
- stars
- adaptive FPS

Astronomical reality sync itself is always active: Sun/Moon position, phase, twilight and scene-light calculations are not converted into a random decorative mode.

## Battery architecture

The engine separates three frequencies:

- Animation frames: visual motion only.
- Astronomy/reality composition: controlled local recalculation.
- Weather network refresh: scheduled/cached and never tied to animation FPS.

This keeps a continuously animated wallpaper possible without performing network or GPS work on every frame.

## Accuracy boundary

The renderer can only animate the weather state available from the shared weather pipeline. It cannot guarantee hyperlocal physical observation when a weather provider itself misses a very local shower.

The precipitation-first resolver and 15-minute cross-check remain the current right-now condition authority, and later radar/observation integrations can strengthen the same shared reality state without replacing this animation architecture.
